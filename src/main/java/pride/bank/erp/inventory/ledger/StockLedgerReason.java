/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.inventory.ledger;

/** Why stock entered or left a store. Every ledger row carries one. */
public enum StockLedgerReason {
    /** Goods received against a purchase order. */
    RECEIPT,
    /** Issued to a person; the store's stock is consumed. */
    ISSUANCE,
    /** Left a store on its way to another. */
    TRANSFER_OUT,
    /** Arrived at a store from another. */
    TRANSFER_IN,
    /** Handed to a courier — into custody, out of the source store. */
    CUSTODY_OUT,
    /** Taken back out of a courier's custody at the destination. */
    CUSTODY_IN,
    /** Returned to a store by the person holding it. */
    RETURN_TO_STORE,
    /** Corrected to match a physical count. */
    STOCK_TAKE,
    /** A receipt undone because its stock order was deleted. */
    STOCK_REVERSAL,
    /**
     * Written by a path that has not yet been given a reason.
     *
     * <p>Never desirable, but far better than a silent gap: a row that says "something changed this
     * balance and we did not record why" is still evidence, and it makes the offending caller easy
     * to find in the report.
     */
    UNSPECIFIED
}
