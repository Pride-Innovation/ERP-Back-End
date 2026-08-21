package pride.bank.erp.asset;

/**
 * Per-category asset counts for the dashboard.
 *
 * <p>The four state counts partition {@link #getTotal()} exactly: every asset falls into one and
 * only one of assigned / unassigned / in-maintenance / disposed. That is what lets the condition
 * meter and the stacked bars reconcile with the headline total instead of quietly disagreeing
 * with it.
 */
public interface AssetTypeStatsProjection {
    String getAssetType();

    /** Every asset on the register in this category, disposed ones included. */
    Long getTotal();

    Long getInMaintenance();

    Long getUnassigned();

    Long getAssigned();

    /**
     * Assets written off.
     *
     * <p>Absent until now, and its absence was not neutral: the {@code assigned} bucket was defined
     * as "any status that is not in-maintenance and not require-update", which swept every disposed
     * asset into it. The "In Use" figure on the dashboard therefore counted items the bank had
     * already written off.
     */
    Long getDisposed();
}
