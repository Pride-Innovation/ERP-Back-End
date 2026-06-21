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
 * </ul>
 */
public enum StoreType {
    ADMIN,
    IT,
    DISPOSAL
}
