/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.inventory.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalance;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes and verifies the stock ledger.
 *
 * <p>Recording happens inside the same transaction as the balance change, so a rolled-back movement
 * leaves no entry and a committed one always has exactly one. The two can never disagree about
 * whether something happened — only, if a bug bypasses {@code InventoryService} entirely, about the
 * amount. That is what {@link #verify()} is for.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockLedgerService {

    private final StockLedgerRepository ledgerRepository;
    private final StoreBalanceRepository storeBalanceRepository;
    private final CurrentUserService currentUserService;

    /** Records one balance change. Called by {@code InventoryService}, not directly by features. */
    @Transactional
    public void record(Store store, Commodity commodity, int quantityDelta, int balanceAfter,
                       LedgerContext context) {
        if (quantityDelta == 0) return;

        LedgerContext ctx = context != null ? context : LedgerContext.unspecified();
        if (ctx.reason() == StockLedgerReason.UNSPECIFIED) {
            // Loud on purpose: it means a code path is changing stock without saying why, and the
            // entry alone will not explain itself later.
            log.warn("Ledger entry written with no reason — store {}, commodity {}, delta {}",
                    store.getId(), commodity.getId(), quantityDelta);
        }

        User actor = safeCurrentUser();
        ledgerRepository.save(StockLedgerEntry.builder()
                .store(store)
                .commodity(commodity)
                .quantityDelta(quantityDelta)
                .balanceAfter(balanceAfter)
                .reason(ctx.reason())
                .sourceType(ctx.sourceType())
                .sourceId(ctx.sourceId())
                .note(ctx.note())
                .occurredAt(LocalDateTime.now())
                .actorId(actor != null ? actor.getId() : null)
                .build());
    }

    /**
     * Recomputes every balance from the ledger and reports where the two disagree.
     *
     * <p>A discrepancy means something changed a balance without going through the ledger. Rows
     * created before the ledger existed will show as drift on their first run — that is expected,
     * and is exactly the historical position the ledger cannot account for.
     */
    @Transactional(readOnly = true)
    public LedgerVerification verify() {
        Map<String, Integer> ledgerTotals = new HashMap<>();
        for (Object[] row : ledgerRepository.sumDeltasByStoreAndCommodity()) {
            if (row[0] == null || row[1] == null) continue;
            ledgerTotals.put(key(((Number) row[0]).longValue(), ((Number) row[1]).longValue()),
                    row[2] == null ? 0 : ((Number) row[2]).intValue());
        }

        List<LedgerVerification.Discrepancy> discrepancies = new ArrayList<>();
        int checked = 0;

        for (StoreBalance balance : storeBalanceRepository.findAll()) {
            if (balance.getStore() == null || balance.getCommodity() == null) continue;
            checked++;

            int expected = ledgerTotals.getOrDefault(
                    key(balance.getStore().getId(), balance.getCommodity().getId()), 0);

            if (expected != balance.getQuantity()) {
                discrepancies.add(LedgerVerification.Discrepancy.builder()
                        .storeId(balance.getStore().getId())
                        .storeName(balance.getStore().getName())
                        .commodityId(balance.getCommodity().getId())
                        .commodityName(balance.getCommodity().getName())
                        .balanceQuantity(balance.getQuantity())
                        .ledgerQuantity(expected)
                        .difference(balance.getQuantity() - expected)
                        .build());
            }
        }

        return LedgerVerification.builder()
                .balancesChecked(checked)
                .discrepancies(discrepancies)
                .inSync(discrepancies.isEmpty())
                .build();
    }

    /** The full history of one commodity in one store. */
    @Transactional(readOnly = true)
    public List<StockLedgerEntry> history(Long storeId, Long commodityId) {
        return ledgerRepository.findByStore_IdAndCommodity_IdOrderByIdAsc(storeId, commodityId);
    }

    @Transactional(readOnly = true)
    public List<StockLedgerEntry> entries(Long storeId, LocalDateTime from, LocalDateTime to) {
        return ledgerRepository.findForReport(storeId, from, to);
    }

    private static String key(Long storeId, Long commodityId) {
        return storeId + ":" + commodityId;
    }

    /** Seeding and scheduled jobs run without a request, so there is no current user to read. */
    private User safeCurrentUser() {
        try {
            return currentUserService.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }
}
