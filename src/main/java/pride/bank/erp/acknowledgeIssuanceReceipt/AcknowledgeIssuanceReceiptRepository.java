package pride.bank.erp.acknowledgeIssuanceReceipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AcknowledgeIssuanceReceiptRepository extends JpaRepository<AcknowledgeIssuanceReceipt, Long> {
    Optional<AcknowledgeIssuanceReceipt> findByRequestId(Long requestId);
}
