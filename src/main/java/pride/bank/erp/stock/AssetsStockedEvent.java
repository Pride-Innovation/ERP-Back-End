/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Raised when assets have been registered from a stocking operation, so the responsible team can be
 * told to prepare them.
 *
 * <p>Published inside the transaction but <b>delivered after commit</b> (see
 * {@code StockNotificationListener}). Sending mail inline previously meant an SMTP outage could roll
 * back an otherwise-good receipt — the goods were physically in the store, but the system had no
 * record of them.
 */
@Getter
@AllArgsConstructor
public class AssetsStockedEvent {

    /** Mailbox of the team that owns the asset category (AssetType.ownerGroupEmail). */
    private final String recipient;

    private final Stock stock;

    /** Salutation line, e.g. "Dear IT Equipment Team,". */
    private final String greeting;

    /** Rendered HTML body listing what was stocked. */
    private final String body;
}
