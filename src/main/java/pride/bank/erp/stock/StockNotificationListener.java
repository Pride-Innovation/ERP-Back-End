/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pride.bank.erp.notification.Email;

/**
 * Delivers stocking notifications once the surrounding transaction has committed.
 *
 * <p>Two properties matter here: the mail only goes out if the stocking actually persisted (no
 * "your items have arrived" for a rolled-back receipt), and a mail failure can no longer roll back
 * the receipt, because there is no longer a transaction to roll back.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockNotificationListener {

    private final Email emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssetsStocked(AssetsStockedEvent event) {
        try {
            emailService.sendEmailVerification(
                    event.getRecipient(),
                    String.format("Stock LPO ID: %s", event.getStock().getLpoNumber()),
                    null,
                    event.getGreeting(),
                    "View Stock",
                    event.getBody(),
                    "stocks",
                    null,
                    event.getStock(),
                    null,
                    null,
                    null
            );
        } catch (Exception e) {
            // The stocking is already committed and correct; a failed notification must not
            // surface as a failed receipt. Log loudly instead.
            log.error("Failed to send stocking notification to {} for stock {}",
                    event.getRecipient(), event.getStock().getId(), e);
        }
    }
}
