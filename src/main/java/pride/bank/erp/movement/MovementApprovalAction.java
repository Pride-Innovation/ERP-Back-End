package pride.bank.erp.movement;

/** What happened at a movement-approval tier. */
public enum MovementApprovalAction {
    /** The tier is the current approver, awaiting a decision. */
    PENDING,
    /** The tier approved; the movement advances to the next tier or to INITIATED. */
    APPROVED,
    /** The tier rejected; the movement is cancelled and the initiator notified. */
    REJECTED
}
