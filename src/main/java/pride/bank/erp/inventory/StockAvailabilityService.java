/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.issuedCommodity.IssuedCommodityRepository;
import pride.bank.erp.movement.MovementRepository;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalance;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.store.StoreType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How much of a consumable a store can still promise: what is on the shelf, less what is already
 * spoken for.
 *
 * <h2>Why a balance alone was the wrong answer</h2>
 * Stock is debited late - a movement at custody handoff or completion, an issuance at approval - so
 * between the promise and the debit the units still sit in the balance. Two movements could each be
 * created against the same 10 units; the second failed only when the courier came to collect it,
 * with "Insufficient stock", long after anyone could do anything about it.
 *
 * <h2>What counts as promised</h2>
 * Exactly what will later be debited from this store, read from the same flags the debits use:
 * <ul>
 *   <li>movements drawing from it as source, not yet past custody handoff or completion;</li>
 *   <li>movements a consignment landed here, waiting for their recipient to sign;</li>
 *   <li>for a branch's own Admin store, issuances there not yet approved (and not on a rejected or
 *       deleted request) - the approval is what debits them.</li>
 * </ul>
 * Nothing is written: a promise is the open movement or issuance itself, so cancelling or rejecting
 * one releases its stock with no bookkeeping to forget.
 */
@Service
@RequiredArgsConstructor
public class StockAvailabilityService {

    private final StoreBalanceRepository storeBalanceRepository;
    private final MovementRepository movementRepository;
    private final IssuedCommodityRepository issuedCommodityRepository;

    /** Units already promised out of this store, by commodity id. Commodities with none are absent. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> promised(Store store) {
        Map<Long, Integer> promised = new HashMap<>();
        if (store == null || store.getId() == null) return promised;
        addRows(promised, movementRepository.findPromisedFromSource(store.getId()));
        addRows(promised, movementRepository.findPromisedAtLanding(store.getId()));
        if (isBranchAdminStore(store)) {
            addRows(promised, issuedCommodityRepository.findPromisedByUnsettledIssuances(store.getLocation().getId()));
        }
        return promised;
    }

    /** On hand less promised, never below zero. */
    @Transactional(readOnly = true)
    public int available(Store store, Commodity commodity) {
        int onHand = onHand(store, commodity);
        return Math.max(0, onHand - promised(store).getOrDefault(commodity.getId(), 0));
    }

    /**
     * Refuses unless the store can still promise every quantity asked for, naming each shortfall with
     * what is on hand and what is already spoken for - so the reader can see why, not just that.
     *
     * @param wanted quantities by commodity; a commodity asked for on two lines should be summed first
     */
    @Transactional(readOnly = true)
    public void requireAvailable(Store store, Map<Commodity, Integer> wanted) {
        Map<Long, Integer> promised = promised(store);
        List<String> shortfalls = new ArrayList<>();
        for (Map.Entry<Commodity, Integer> e : wanted.entrySet()) {
            Commodity commodity = e.getKey();
            int asked = e.getValue() == null ? 0 : e.getValue();
            int onHand = onHand(store, commodity);
            int spoken = promised.getOrDefault(commodity.getId(), 0);
            int free = Math.max(0, onHand - spoken);
            if (asked > free) {
                shortfalls.add(String.format("'%s': %d asked, %d available (%d on hand, %d already promised)",
                        commodity.getName(), asked, free, onHand, spoken));
            }
        }
        if (!shortfalls.isEmpty()) {
            throw new IllegalArgumentException("Not enough stock in " + store.getName() + ": "
                    + String.join("; ", shortfalls));
        }
    }

    /** Sums quantities per commodity, keeping the order lines were given in. */
    public static Map<Commodity, Integer> sumByCommodity(List<Map.Entry<Commodity, Integer>> lines) {
        Map<Long, Commodity> byId = new LinkedHashMap<>();
        Map<Long, Integer> totals = new LinkedHashMap<>();
        for (Map.Entry<Commodity, Integer> line : lines) {
            byId.putIfAbsent(line.getKey().getId(), line.getKey());
            totals.merge(line.getKey().getId(), line.getValue() == null ? 0 : line.getValue(), Integer::sum);
        }
        Map<Commodity, Integer> out = new LinkedHashMap<>();
        totals.forEach((id, qty) -> out.put(byId.get(id), qty));
        return out;
    }

    private int onHand(Store store, Commodity commodity) {
        return storeBalanceRepository.findByStore_IdAndCommodity_Id(store.getId(), commodity.getId())
                .map(StoreBalance::getQuantity)
                .orElse(0);
    }

    /** A branch's own Admin store - the one an issuance there draws from. Department stores are not. */
    private static boolean isBranchAdminStore(Store store) {
        return store.getStoreType() == StoreType.ADMIN && store.getDepartment() == null && store.getLocation() != null;
    }

    private static void addRows(Map<Long, Integer> into, List<Object[]> rows) {
        for (Object[] row : rows) {
            if (row[0] == null || row[1] == null) continue;
            into.merge(((Number) row[0]).longValue(), ((Number) row[1]).intValue(), Integer::sum);
        }
    }
}
