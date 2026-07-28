/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.inventory.InventoryService;
import pride.bank.erp.inventory.ledger.LedgerContext;
import pride.bank.erp.inventory.ledger.StockLedgerReason;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalance;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Takes stock back out of a store when the receipt that put it there is undone.
 *
 * <p>Deleting a stock order used to soft-delete the order, its GRNs, receipts and assets while
 * leaving the quantity it had credited sitting on the shelf figure forever. Reversing it is the
 * correct behaviour, but doing so quietly would be the same invisible balance change the rest of
 * this package exists to prevent — so every reversal is written as a reason-coded
 * {@link StockAdjustment} and shows up in the variance report like any other correction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockReversalService {

    private final StockAdjustmentRepository adjustmentRepository;
    private final StoreBalanceRepository storeBalanceRepository;
    private final InventoryService inventoryService;
    private final CurrentUserService currentUserService;

    /**
     * Debits {@code quantities} from {@code store}, recording an adjustment for each.
     *
     * <p>If the goods have already been issued out there is nothing left to take back, and the debit
     * fails rather than driving the balance negative. That is deliberate: an order whose stock has
     * been consumed is a data-integrity question for a person, not something to paper over by
     * inventing a negative balance.
     *
     * @param quantities commodity → units to remove
     * @param note       what is being reversed, for the adjustment trail
     */
    @Transactional
    public int reverse(Store store, Map<Commodity, Integer> quantities, String note) {
        if (store == null || quantities.isEmpty()) return 0;

        User actor = currentUserService.getCurrentUser();
        int reversed = 0;

        for (Map.Entry<Commodity, Integer> entry : quantities.entrySet()) {
            Commodity commodity = entry.getKey();
            int quantity = entry.getValue();
            if (quantity <= 0) continue;

            int before = storeBalanceRepository
                    .findByStore_IdAndCommodity_Id(store.getId(), commodity.getId())
                    .map(StoreBalance::getQuantity)
                    .orElse(0);

            if (before < quantity) {
                throw new IllegalArgumentException(String.format(
                        "Cannot reverse %d %s from %s — only %d remain, so some have already been "
                                + "issued out. Resolve that before deleting this stock.",
                        quantity, commodity.getName(), store.getName(), before));
            }

            inventoryService.debitConsumable(store, commodity, quantity,
                    LedgerContext.of(StockLedgerReason.STOCK_REVERSAL, "STOCK_DELETE", null, note));

            adjustmentRepository.save(StockAdjustment.builder()
                    .store(store)
                    .commodity(commodity)
                    .quantityDelta(-quantity)
                    .quantityBefore(before)
                    .quantityAfter(before - quantity)
                    .reason(AdjustmentReason.STOCK_REVERSAL)
                    .note(note)
                    .postedAt(LocalDateTime.now())
                    .postedBy(actor != null ? actor.getId() : null)
                    .build());
            reversed++;
        }

        log.info("Reversed {} commodity line(s) out of store '{}': {}", reversed, store.getName(), note);
        return reversed;
    }
}
