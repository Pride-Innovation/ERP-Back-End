package pride.bank.erp.assetAssignmentHostory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetAssignmentHistoryRepository extends JpaRepository<AssetAssignmentHistory, Long> {
    @Query("SELECT aah FROM AssetAssignmentHistory aah WHERE aah.asset.id = :assetId AND aah.endDate IS NULL")
    AssetAssignmentHistory findActiveByAssetId(Long assetId);
}
