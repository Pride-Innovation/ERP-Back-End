package pride.bank.erp.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pride.bank.erp.asset.AssetTypeSummaryView;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link StoreBalance} (on-hand quantities). Formerly {@code StoreRepository};
 * renamed when the {@code Store} name was repurposed for the store container. The underlying table
 * is still {@code store}, so native queries are unchanged.
 *
 * <p><b>Courier custody caveat:</b> a {@link StoreType#COURIER} custody store is anchored to the
 * Head Office branch (its {@code location} is non-null only to satisfy the schema), so its balances
 * carry {@code branch = Head Office}. That means "one balance per (branch, commodity)" no longer
 * holds — Head Office can have both its ADMIN row and a courier-custody row for the same commodity.
 * Every branch-scoped query below therefore excludes COURIER stores, and single-result lookups are
 * scoped to a specific store (never branch-only), so in-transit custody can neither crash a
 * {@code getSingleResult} nor masquerade as branch stock.
 */
@Repository
public interface StoreBalanceRepository extends JpaRepository<StoreBalance, Long> {

    /**
     * The (unique) balance of a commodity in a branch's store of the given type. Use with
     * {@link StoreType#ADMIN} for availability checks — branch-only lookups are unsafe because a
     * courier-custody row shares the same branch (see class doc).
     */
    Optional<StoreBalance> findByBranchIdAndStore_StoreTypeAndCommodity_Id(
            Long branchId, StoreType storeType, Long commodityId);

    @Query("SELECT s FROM StoreBalance s " +
            "JOIN FETCH s.commodity " +
            "JOIN FETCH s.branch " +
            "WHERE s.branch.id = :branchId " +
            "AND (s.store IS NULL OR s.store.storeType <> pride.bank.erp.store.StoreType.COURIER)")
    List<StoreBalance> findAllByBranchIdWithCommodityAndBranch(@Param("branchId") Long branchId);

    @Query("SELECT s FROM StoreBalance s " +
            "WHERE s.branch.id = :branchId " +
            "AND s.commodity.assetType.id = :assetTypeId " +
            "AND (s.store IS NULL OR s.store.storeType <> pride.bank.erp.store.StoreType.COURIER)")
    Page<StoreBalance> findByBranchIdAndCommodity_AssetTypeId(
            @Param("branchId") Long branchId, @Param("assetTypeId") Long assetTypeId, Pageable pageable);

    /** All (non-courier) balance lines of a branch, paged — used when no asset-category filter is given. */
    @Query("SELECT s FROM StoreBalance s " +
            "WHERE s.branch.id = :branchId " +
            "AND (s.store IS NULL OR s.store.storeType <> pride.bank.erp.store.StoreType.COURIER)")
    Page<StoreBalance> findByBranchId(@Param("branchId") Long branchId, Pageable pageable);

    // ── Store-scoped listings: each store page (Admin / IT / Disposal) sees only its own lines.
    // Null-store legacy rows are backfilled to the branch ADMIN store by StoreSeeder on startup,
    // so the implicit inner join on the store container cannot hide live balances. These already
    // filter to a concrete store type, so courier custody is inherently excluded.
    Page<StoreBalance> findByBranchIdAndStore_StoreTypeAndCommodity_AssetTypeId(
            Long branchId, StoreType storeType, Long assetTypeId, Pageable pageable);

    Page<StoreBalance> findByBranchIdAndStore_StoreType(Long branchId, StoreType storeType, Pageable pageable);

    @Query("SELECT s FROM StoreBalance s " +
            "WHERE s.branch.id = :branchId " +
            "AND s.commodity.assetType.id = :assetTypeId " +
            "AND (s.store IS NULL OR s.store.storeType <> pride.bank.erp.store.StoreType.COURIER)")
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
                LEFT JOIN store_container sc ON sc.id = s.store_id
                WHERE at.name = :assetTypeName AND s.quantity > 0
                  AND (sc.store_type IS NULL OR sc.store_type <> 'COURIER')
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
                  AND (s.store IS NULL OR s.store.storeType <> pride.bank.erp.store.StoreType.COURIER)
                GROUP BY c.name
            """)
    List<CommoditySummary> getStationeryCommoditiesByCurrentMonthByBranch(@Param("branchId") Long branchId);

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
