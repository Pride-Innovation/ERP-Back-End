package pride.bank.erp.workflowinstance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RequestWorkflowInstanceRepository extends JpaRepository<RequestWorkflowInstance, Long> {

    /** Returns the most-recent instance for a request (handles resubmissions). */
    Optional<RequestWorkflowInstance> findTopByRequestIdOrderByIdDesc(Long requestId);

    List<RequestWorkflowInstance> findByRequestId(Long requestId);

    /** All instances in a given status — used by the escalation sweep to find pending work. */
    List<RequestWorkflowInstance> findByStatus(InstanceStatus status);

    /**
     * The kind of action each of these requests is currently waiting for.
     *
     * <h2>Why a listing needs it</h2>
     * A request's <em>status</em> does not say what is due next. {@code unitAcknowledged} sits among
     * the approval codes while the workflow has already moved to issuance, so a row menu built from
     * status offers Approve on a request nobody can approve — and the engine would apply that
     * approval to the <b>issuance</b> step. The step says what is due; the status is a consequence of
     * the last action taken.
     *
     * <p>Fetched for a whole page in one query rather than per row: resolving it through the instance
     * and its workflow one request at a time is the shape that turns a listing into N+1.
     *
     * <p>Only IN_PROGRESS instances answer. A finished or rejected workflow waits for nothing, and a
     * request with no instance predates the engine — both come back absent, which the caller reads as
     * "no action is due", and no action is then offered.
     *
     * <p>The approver subject comes back with the step type because the two together name the
     * stage, and the type alone does not. The <b>issuance approval</b> is modelled as an ordinary
     * {@code REQUEST_APPROVAL} step whose {@code approverSubject} is {@code ISSUER} — it approves
     * the issuer's issuance rather than the requester's request, and
     * {@link WorkflowEngineService#resolveStatusCode} already treats it as its own stage. Reported
     * as a plain approval it would offer <em>Approve Request</em> on a request already issued, which
     * advances the step without ever creating the fulfilment movement that
     * {@code POST /approve-issuance} exists to create.
     *
     * @return rows of {@code [requestId, StepType, ApproverSubject]}
     */
    @Query("SELECT i.request.id, s.stepType, s.approverSubject FROM RequestWorkflowInstance i "
            + "JOIN i.workflow w JOIN w.steps s "
            + "WHERE i.request.id IN :requestIds "
            + "AND i.status = pride.bank.erp.workflowinstance.InstanceStatus.IN_PROGRESS "
            + "AND s.stepOrder = i.currentStepOrder")
    List<Object[]> findCurrentStepTypes(@Param("requestIds") Collection<Long> requestIds);
}
