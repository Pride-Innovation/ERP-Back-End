package pride.bank.erp.consignment;

/**
 * Lifecycle of a {@link Consignment} — the physical journey, separate from the obligations riding on
 * it.
 *
 * <p>Deliberately shorter than {@code MovementStatus}: a journey is not completed, it arrives. What
 * happens after arrival belongs to each movement, which completes when its own recipient signs.
 */
public enum ConsignmentStatus {
    /** Being assembled. Movements can still be added and removed. */
    DRAFT,
    /** Handed to the courier, with a signed dispatch note. Contents are frozen. */
    DISPATCHED,
    /** In the courier's custody. Stock has left the source stores and sits against the courier. */
    IN_TRANSIT,
    /** Landed at the destination. Stock is credited to the destination store; hand-overs can begin. */
    ARRIVED,
    /** Abandoned before dispatch. Its movements are released back to be sent another way. */
    CANCELLED
}
