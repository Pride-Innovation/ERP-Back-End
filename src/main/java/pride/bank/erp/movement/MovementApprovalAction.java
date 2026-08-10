package pride.bank.erp.movement;

/** What happened at a movement-approval tier. */
public enum MovementApprovalAction {
    /** The tier is the current approver, awaiting a decision. */
    PENDING,
    /** The tier approved; the movement advances to the next tier or to INITIATED. */
    APPROVED,
    /** The tier rejected; the movement is canceled and the initiator notified. */
    REJECTED,
    /**
     * No approver could be resolved and the initiator chose to proceed anyway.
     *
     * <p>Recorded as a tier in the trail rather than left implicit, so an unapproved movement is
     * never indistinguishable from an approved one: the actor is whoever confirmed it, and the
     * comment is their stated reason. This is the exception being documented, not waived.
     */
    BYPASSED
}
