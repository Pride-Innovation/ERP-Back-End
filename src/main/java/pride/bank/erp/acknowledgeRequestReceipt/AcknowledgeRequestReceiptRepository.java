package pride.bank.erp.acknowledgeRequestReceipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AcknowledgeRequestReceiptRepository extends JpaRepository<AcknowledgeRequestReceipt, Long> {
    Optional<AcknowledgeRequestReceipt> findByRequestId(Long requestId);
}
