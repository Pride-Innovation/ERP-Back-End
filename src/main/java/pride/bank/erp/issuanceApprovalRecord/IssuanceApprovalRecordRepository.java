package pride.bank.erp.issuanceApprovalRecord;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssuanceApprovalRecordRepository extends JpaRepository<IssuanceApprovalRecord, Long> {
}
