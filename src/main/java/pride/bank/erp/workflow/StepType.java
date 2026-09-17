package pride.bank.erp.workflow;

public enum StepType {
    /** Approve (or reject) the request. */
    REQUEST_APPROVAL,
    /** Acknowledge the request was received (post-approval handshake). */
    ACKNOWLEDGE_REQUEST,
    /** Issue the requested items to the requester. */
    ISSUANCE,
    /** Acknowledge receipt of the issued items. */
    ACKNOWLEDGE_RECEIPT
}
