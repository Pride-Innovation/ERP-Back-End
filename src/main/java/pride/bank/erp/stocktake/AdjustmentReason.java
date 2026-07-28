/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

/**
 * Why a counted quantity differs from the system's.
 *
 * <p>Required on every non-zero variance. A stock take that only records <i>that</i> the number
 * changed is barely better than editing the balance by hand — the reason is what makes the variance
 * report worth reading, and what separates a counting mistake from a loss that needs investigating.
 */
public enum AdjustmentReason {
    /** The earlier figure was simply wrong; no physical loss. */
    MISCOUNT,
    /** Physically present but unusable. */
    DAMAGED,
    /** Missing with no explanation — worth flagging for investigation. */
    LOST_OR_STOLEN,
    /** Past its usable date. */
    EXPIRED,
    /** More on the shelf than recorded — usually an unrecorded return or receipt. */
    FOUND,
    /** A keying or posting error elsewhere in the system. */
    DATA_ENTRY_ERROR,
    /**
     * A receipt was undone — the stock it brought in is being taken back out.
     *
     * <p>Raised when a stock order is deleted: the goods it credited have to leave the store, and
     * doing that silently would be the same invisible balance change this whole model exists to
     * prevent.
     */
    STOCK_REVERSAL,
    /** Anything else; a note is mandatory. */
    OTHER
}
