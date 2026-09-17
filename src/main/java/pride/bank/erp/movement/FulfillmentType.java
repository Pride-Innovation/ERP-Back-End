package pride.bank.erp.movement;

/**
 * How an approved request is fulfilled after issuance approval (CLAUDE.md §6). Decided from the
 * source store's location versus the requester's location; consumed by the Phase 3 fulfillment hook.
 *
 * <ul>
 *   <li>{@link #DIRECT_ISSUANCE} — same location: issue straight to the requester (no movement,
 *       no courier, no BOM).</li>
 *   <li>{@link #STORE_MOVEMENT} — different locations: create an inter-location {@link Movement},
 *       then issue locally once received.</li>
 * </ul>
 */
public enum FulfillmentType {
    DIRECT_ISSUANCE,
    STORE_MOVEMENT
}
