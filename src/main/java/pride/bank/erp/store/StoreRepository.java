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

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByBranchAndCommodity(Branch branch, Commodity commodity);

    @Query("SELECT s FROM Store s " +
            "JOIN FETCH s.commodity " +
            "JOIN FETCH s.branch " +
            "WHERE s.branch.id = :branchId")
    List<Store> findAllByBranchIdWithCommodityAndBranch(Long branchId);

    Page<Store> findByBranchIdAndCommodity_AssetTypeId(Long branchId, Long assetTypeId, Pageable pageable);

    @Query("SELECT s FROM Store s " +
            "WHERE s.branch.id = :branchId " +
            "AND s.commodity.assetType.id = :assetTypeId")
    List<Store> findByBranchAndAssetType(@Param("branchId") Long branchId,
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
                FROM Store s
                JOIN s.commodity c
                JOIN c.assetType at
                WHERE at.name = 'Stationery'
                  AND s.branch.id = :branchId
                GROUP BY c.name
            """)
    List<CommoditySummary> getStationeryCommoditiesByCurrentMonthByBranch(@Param("branchId") Long branchId);

}
