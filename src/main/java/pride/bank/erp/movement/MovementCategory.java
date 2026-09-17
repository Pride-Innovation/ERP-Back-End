package pride.bank.erp.movement;

/**
 * Whether a movement crosses physical locations (CLAUDE.md §9). Derived from the source store's
 * location versus the destination's location — never set by hand.
 *
 * <ul>
 *   <li>{@link #INTER_LOCATION} — different locations (e.g. Head Office → Branch). Requires courier,
 *       dispatch/receipt handling and BOM involvement.</li>
 *   <li>{@link #INTRA_LOCATION} — same location (e.g. Admin Store → Department/User). Immediate,
 *       no courier; only a receipt acknowledgement.</li>
 * </ul>
 */
public enum MovementCategory {
    INTER_LOCATION,
    INTRA_LOCATION
}
