package pride.bank.erp.workflowinstance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RequestWorkflowInstanceRepository extends JpaRepository<RequestWorkflowInstance, Long> {
    Optional<RequestWorkflowInstance> findByRequestId(Long requestId);
}
