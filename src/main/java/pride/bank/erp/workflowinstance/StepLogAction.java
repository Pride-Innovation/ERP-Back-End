package pride.bank.erp.workflowinstance;

public enum StepLogAction {
    PENDING,
    APPROVED,
    REJECTED,
    ISSUED,
    ACKNOWLEDGED,
    /** Marks a log entry that was pending when the officer resubmitted a rejected request. */
    SUPERSEDED
}
