/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    @Query(value = """
                SELECT at.name AS assetTypeName,
                       COUNT(a.id) AS totalCount,
                       MAX(a.create_date) AS lastUpdatedDate
                FROM asset a
                LEFT JOIN commodity c ON a.commodity_id = c.id
                LEFT JOIN asset_type at ON c.asset_type_id = at.id
                WHERE at.name IS NOT NULL
                GROUP BY at.name
            """, nativeQuery = true)
    List<AssetTypeSummaryView> getAssetTypeSummary();

    @Query("""
            SELECT
                a.assetType.name AS assetType,
                COUNT(a.id) AS total,
                SUM(CASE WHEN a.assetStatus.status = 'inMaintenance' THEN 1 ELSE 0 END) AS inMaintenance,
                SUM(CASE WHEN a.assetStatus.status = 'requireUpdate' THEN 1 ELSE 0 END) AS unassigned
            FROM Asset a
            WHERE a.assetType.name IN ('Office Equipment', 'IT Equipment', 'Fleet')
              AND a.branch.id = :branchId
            GROUP BY a.assetType.name
            ORDER BY a.assetType.name
            """)
    List<AssetTypeStatsProjection> getAssetStatsByType(Long branchId);

    @Query("""
                SELECT a
                FROM Asset a
                JOIN FETCH a.assetType at
                LEFT JOIN FETCH a.assetStatus s
                WHERE a.assignedTo.id = :userId
            """)
    List<Asset> findAllByAssignedToIdWithTypeAndStatus(@Param("userId") Long userId);

    Optional<Asset> findByTagName(@Param("tagName") String tagName);

    Optional<Asset> findByEngravedNumber(@Param("tagName") String tagName);

    List<Asset> findByStockId(Long stockId);

    List<Asset> findByAssetTypeId(@Param("assetTypeId") Long assetTypeId);

    List<Asset> findByAssetTypeIdAndCreateDateBetween(
            @Param("assetTypeId") Long assetTypeId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
