package pride.bank.erp.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pride.bank.erp.asset.AssetTypeSummaryView;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.commodity.Commodity;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link StoreBalance} (on-hand quantities). Formerly {@code StoreRepository};
 * renamed when the {@code Store} name was repurposed for the store container. The underlying table
 * is still {@code store}, so native queries are unchanged.
 */
@Repository
public interface StoreBalanceRepository extends JpaRepository<StoreBalance, Long> {

    Optional<StoreBalance> findByBranchAndCommodity(Branch branch, Commodity commodity);

    @Query("SELECT s FROM StoreBalance s " +
            "JOIN FETCH s.commodity " +
            "JOIN FETCH s.branch " +
            "WHERE s.branch.id = :branchId")
    List<StoreBalance> findAllByBranchIdWithCommodityAndBranch(Long branchId);

    Page<StoreBalance> findByBranchIdAndCommodity_AssetTypeId(Long branchId, Long assetTypeId, Pageable pageable);

    @Query("SELECT s FROM StoreBalance s " +
            "WHERE s.branch.id = :branchId " +
            "AND s.commodity.assetType.id = :assetTypeId")
    List<StoreBalance> findByBranchAndAssetType(@Param("branchId") Long branchId,
                                                @Param("assetTypeId") Long assetTypeId);

    @Query(value = """
                SELECT
                    at.name AS assetTypeName,
                    SUM(s.quantity) AS totalCount,
                    MAX(s.create_date) AS lastUpdatedDate
                FROM store s
                JOIN commodity c ON c.id = s.commodity_id
                JOIN asset_type at ON at.id = c.asset_type_id
                WHERE at.name = :assetTypeName AND s.quantity > 0
                GROUP BY at.name
            """, nativeQuery = true)
    AssetTypeSummaryView findAssetTypeSummary(@Param("assetTypeName") String assetTypeName);

    @Query("""
                SELECT c.name AS commodityName, SUM(s.quantity) AS totalQuantity
                FROM StoreBalance s
                JOIN s.commodity c
                JOIN c.assetType at
                WHERE at.name = 'Stationery'
                  AND s.branch.id = :branchId
                GROUP BY c.name
            """)
    List<CommoditySummary> getStationeryCommoditiesByCurrentMonthByBranch(@Param("branchId") Long branchId);

    Optional<StoreBalance> findByBranchIdAndCommodityId(Long branchId, Long commodityId);

    /** Balance of a commodity within a specific store container — the unit the ledger mutates. */
    Optional<StoreBalance> findByStore_IdAndCommodity_Id(Long storeId, Long commodityId);

    /** Balances whose container link has not yet been backfilled (used by the one-time migration). */
    List<StoreBalance> findByStoreIsNull();

    // ── Reporting / visibility (§11-12) ──────────────────────────────────────
    List<StoreBalance> findByStore_Id(Long storeId);

    List<StoreBalance> findByStore_Location_Id(Long locationId);

    List<StoreBalance> findByStore_StoreType(StoreType storeType);

    List<StoreBalance> findByStore_Department_Id(Long departmentId);

    /** Low-stock monitoring: balances at or below a threshold (excludes empty by default at callsite). */
    List<StoreBalance> findByQuantityLessThanEqual(int threshold);

    /** Low-stock by each balance's own configured reorder threshold. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT b FROM StoreBalance b WHERE b.minLevel > 0 AND b.quantity <= b.minLevel")
    List<StoreBalance> findLowStock();
}
