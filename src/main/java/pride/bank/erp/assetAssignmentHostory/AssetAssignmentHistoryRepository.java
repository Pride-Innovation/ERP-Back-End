/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetAssignmentHostory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetAssignmentHistoryRepository extends JpaRepository<AssetAssignmentHistory, Long> {
    @Query("SELECT aah FROM AssetAssignmentHistory aah WHERE aah.asset.id = :assetId AND aah.endDate IS NULL")
    AssetAssignmentHistory findActiveByAssetId(Long assetId);
}
