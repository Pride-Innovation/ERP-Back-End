/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

/**
 * Lifecycle of a stock count. Balances are only ever touched at {@link #POSTED}: counting and
 * approving change nothing on their own, so an abandoned or rejected count leaves the store exactly
 * as it found it.
 */
public enum StockCountStatus {
    /** Opened, lines snapshotted, nothing counted yet. */
    DRAFT,
    /** At least one line has been counted. */
    IN_PROGRESS,
    /** Handed over for review; the sheet is now read-only to the counter. */
    SUBMITTED,
    /** Variances accepted by the approver. Ready to post. */
    APPROVED,
    /** Adjustments written to the balances. Terminal. */
    POSTED,
    /** Abandoned or rejected. Terminal, and applies nothing. */
    CANCELLED
}
