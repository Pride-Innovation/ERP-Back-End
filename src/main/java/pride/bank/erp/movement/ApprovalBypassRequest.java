package pride.bank.erp.movement;

/**
 * A payload that can carry the initiator's confirmation to proceed when no approver exists.
 *
 * <p>An interface rather than four copies of the same two fields: every flow that creates a movement
 * needing approval can hit the same dead end, and the caller-side plumbing should be identical in
 * each. Lombok generates the getters on the implementing DTOs.
 */
public interface ApprovalBypassRequest {

    /** True once the initiator has seen the "no approver" warning and chosen to continue. */
    Boolean getProceedWithoutApproval();

    /** Why. Recorded on the approval trail; required whenever the flag is set. */
    String getBypassReason();
}
