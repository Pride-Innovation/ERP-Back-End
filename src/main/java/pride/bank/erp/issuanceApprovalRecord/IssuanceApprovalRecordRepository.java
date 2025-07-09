package pride.bank.erp.issuanceApprovalRecord;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IssuanceApprovalRecordRepository extends JpaRepository<IssuanceApprovalRecord, Long> {
    Optional<IssuanceApprovalRecord> findByRequestId(Long requestId);
}
