package pride.bank.erp.workflowinstance;

public enum InstanceStatus {
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    /** The instance was superseded when the officer resubmitted a rejected request. */
    SUPERSEDED
}
