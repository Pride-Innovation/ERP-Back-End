package pride.bank.erp.movement;

/** Outcome recorded by the receiving officer when a movement arrives (CLAUDE.md §18). */
public enum ReceiptStatus {
    PENDING,
    RECEIVED_OK,
    RECEIVED_WITH_DISCREPANCY
}
