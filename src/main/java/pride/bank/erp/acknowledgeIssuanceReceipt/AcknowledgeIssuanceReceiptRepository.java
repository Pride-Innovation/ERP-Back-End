package pride.bank.erp.acknowledgeIssuanceReceipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcknowledgeIssuanceReceiptRepository extends JpaRepository<AcknowledgeIssuanceReceipt, Long> {
}
