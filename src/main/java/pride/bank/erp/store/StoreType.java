package pride.bank.erp.store;

/**
 * The kind of store a {@link Store} represents (CLAUDE.md §4).
 *
 * <ul>
 *   <li>{@link #ADMIN} — the main inventory holding store. Every location (branch) has exactly one;
 *       it receives procured stock, issues to users, and is the source/destination of transfers.</li>
 *   <li>{@link #IT} — defective assets awaiting repair, temporary replacements and repaired assets.
 *       Primarily operated from Head Office.</li>
 *   <li>{@link #DISPOSAL} — obsolete / irreparable / written-off assets. Items here are no longer
 *       available for allocation or operational use.</li>
 *   <li>{@link #COURIER} — a courier's custody store. Holds whatever an inter-location movement's
 *       items are while {@code IN_TRANSIT}, between the source store (debited when the courier
 *       takes custody) and the destination store (credited on receipt). One per
 *       {@link pride.bank.erp.courier.Courier}, identified by {@link Store#getCourier()} rather than
 *       by {@link Store#getLocation()} — see {@code Store}'s class doc.</li>
 * </ul>
 */
public enum StoreType {
    ADMIN,
    IT,
    DISPOSAL,
    COURIER
}
