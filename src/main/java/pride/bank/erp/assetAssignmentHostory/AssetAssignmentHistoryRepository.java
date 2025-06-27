package pride.bank.erp.assetAssignmentHostory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetAssignmentHistoryRepository extends JpaRepository<AssetAssignmentHistory, Long> {
}
