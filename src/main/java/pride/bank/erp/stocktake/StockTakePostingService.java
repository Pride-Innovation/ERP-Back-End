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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.inventory.InventoryService;
import pride.bank.erp.inventory.ledger.LedgerContext;
import pride.bank.erp.inventory.ledger.StockLedgerReason;
import pride.bank.erp.store.StoreBalance;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The two transactional halves of posting a count, kept in their own bean on purpose.
 *
 * <p>The drift check has to <b>commit its findings and then refuse</b>. If it ran inside the posting
 * transaction, throwing to reject the post would roll back the very recount flags that tell the
 * counter which lines to redo — the refusal would be correct but useless, leaving the sheet looking
 * approved and unflagged. Separate beans, because a self-call would not pass through the proxy and
 * the propagation would be silently ignored.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockTakePostingService {

    private final StockCountRepository countRepository;
    private final StockCountLineRepository lineRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final StoreBalanceRepository storeBalanceRepository;
    private final InventoryService inventoryService;
    private final CurrentUserService currentUserService;

    /**
     * Flags lines whose balance moved since the sheet was opened, sends the sheet back, and commits
     * that in its own transaction.
     *
     * @return the names of the drifted commodities; empty when the count is safe to post
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> flagDriftedLines(Long countId) throws NotFoundException {
        StockCount count = countRepository.findById(countId)
                .orElseThrow(() -> new NotFoundException("Stock count " + countId + " not found"));

        List<String> drifted = new ArrayList<>();
        for (StockCountLine line : lineRepository.findByStockCount_IdOrderByCommodity_NameAsc(countId)) {
            int current = storeBalanceRepository
                    .findByStore_IdAndCommodity_Id(count.getStore().getId(), line.getCommodity().getId())
                    .map(StoreBalance::getQuantity)
                    .orElse(0);

            if (current != line.getSystemQuantityAtStart()) {
                line.setRecountRequired(true);
                lineRepository.save(line);
                drifted.add(line.getCommodity().getName());
            }
        }

        if (!drifted.isEmpty()) {
            count.setStatus(StockCountStatus.IN_PROGRESS);
            count.setApprovedBy(null);
            count.setApprovedAt(null);
            count.setSubmittedAt(null);
            count.setReviewRemarks(String.format(
                    "Stock moved while this count was open, on %d line(s): %s. They must be counted "
                            + "again before the sheet can be posted. Nothing was adjusted.",
                    drifted.size(), String.join(", ", drifted)));
            countRepository.save(count);
            log.warn("Stock count {} sent back — {} line(s) drifted during the count",
                    count.getReference(), drifted.size());
        }
        return drifted;
    }

    /** Writes the approved variances to the balances, one adjustment per changed line. */
    @Transactional
    public int postApproved(Long countId) throws NotFoundException {
        StockCount count = countRepository.findById(countId)
                .orElseThrow(() -> new NotFoundException("Stock count " + countId + " not found"));

        User poster = currentUserService.getCurrentUser();
        int posted = 0;

        for (StockCountLine line : lineRepository.findByStockCount_IdOrderByCommodity_NameAsc(countId)) {
            Integer variance = line.getVariance();
            if (variance == null || variance == 0) continue;

            // Through the ledger, so a stock take moves stock the same way everything else does.
            if (variance > 0) {
                inventoryService.creditConsumable(count.getStore(), line.getCommodity(), variance,
                        LedgerContext.of(StockLedgerReason.STOCK_TAKE, "STOCK_COUNT", count.getId(),
                                count.getReference() + " — " + line.getReason()));
            } else {
                inventoryService.debitConsumable(count.getStore(), line.getCommodity(), -variance,
                        LedgerContext.of(StockLedgerReason.STOCK_TAKE, "STOCK_COUNT", count.getId(),
                                count.getReference() + " — " + line.getReason()));
            }

            adjustmentRepository.save(StockAdjustment.builder()
                    .store(count.getStore())
                    .commodity(line.getCommodity())
                    .quantityDelta(variance)
                    .quantityBefore(line.getSystemQuantityAtStart())
                    .quantityAfter(line.getCountedQuantity())
                    .reason(line.getReason())
                    .note(line.getNote())
                    .stockCount(count)
                    .approvedBy(count.getApprovedBy() != null ? count.getApprovedBy().getId() : null)
                    .postedAt(LocalDateTime.now())
                    .postedBy(poster != null ? poster.getId() : null)
                    .build());
            posted++;
        }

        count.setStatus(StockCountStatus.POSTED);
        count.setPostedAt(LocalDateTime.now());
        countRepository.save(count);

        log.info("Stock count {} posted — {} adjustment(s) written", count.getReference(), posted);
        return posted;
    }
}
