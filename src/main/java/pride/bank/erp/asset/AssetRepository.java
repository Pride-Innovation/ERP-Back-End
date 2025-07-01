/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
