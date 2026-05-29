package pride.bank.erp.workflowinstance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequestWorkflowInstanceRepository extends JpaRepository<RequestWorkflowInstance, Long> {

    /** Returns the most-recent instance for a request (handles resubmissions). */
    Optional<RequestWorkflowInstance> findTopByRequestIdOrderByIdDesc(Long requestId);

    List<RequestWorkflowInstance> findByRequestId(Long requestId);

    /** All instances in a given status — used by the escalation sweep to find pending work. */
    List<RequestWorkflowInstance> findByStatus(InstanceStatus status);
}
