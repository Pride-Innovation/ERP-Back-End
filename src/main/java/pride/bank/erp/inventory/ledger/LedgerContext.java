/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.inventory.ledger;

/**
 * Why a balance is being changed, and by which document.
 *
 * <p>Passed to every credit and debit so the resulting ledger entry can answer "why is this twelve?"
 * without anyone having to reconstruct it from timestamps.
 *
 * @param reason     the business reason
 * @param sourceType the kind of document, e.g. {@code STOCK_RECEIPT}, {@code MOVEMENT}
 * @param sourceId   that document's id
 * @param note       optional free text
 */
public record LedgerContext(StockLedgerReason reason, String sourceType, Long sourceId, String note) {

    public static LedgerContext of(StockLedgerReason reason, String sourceType, Long sourceId) {
        return new LedgerContext(reason, sourceType, sourceId, null);
    }

    public static LedgerContext of(StockLedgerReason reason, String sourceType, Long sourceId, String note) {
        return new LedgerContext(reason, sourceType, sourceId, note);
    }

    /**
     * For a caller that has not yet been given a reason. The entry is still written — a visible
     * "we don't know why" beats a silent gap in the history.
     */
    public static LedgerContext unspecified() {
        return new LedgerContext(StockLedgerReason.UNSPECIFIED, null, null, null);
    }
}
