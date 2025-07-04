package pride.bank.erp.acknowledgeRequestReceipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcknowledgeRequestReceiptRepository extends JpaRepository<AcknowledgeRequestReceipt, Long> {
}
