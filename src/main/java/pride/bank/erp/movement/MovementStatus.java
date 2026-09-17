package pride.bank.erp.movement;

/**
 * Lifecycle of a {@link Movement}. Inter-location movements walk the full ladder
 * (DISPATCHED → IN_TRANSIT → RECEIVED); intra-location movements may go straight to RECEIVED.
 * The inventory ledger is applied on completion. {@code CANCELLED} is terminal and applies nothing.
 */
public enum MovementStatus {
    DRAFT,
    INITIATED,
    DISPATCHED,
    IN_TRANSIT,
    RECEIVED,
    COMPLETED,
    CANCELLED
}
