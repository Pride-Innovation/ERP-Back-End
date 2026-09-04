package pride.bank.erp.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.AssetTypeSummaryView;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchService;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.inventory.InventoryService;
import pride.bank.erp.inventory.ledger.LedgerContext;
import pride.bank.erp.inventory.ledger.StockLedgerReason;
import pride.bank.erp.stock.CommodityWithDetailsDao;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StoreService {
    private final StoreBalanceRepository storeBalanceRepository;
    private final StoreRepository storeRepository;
    private final BranchService branchService;
    private final CurrentUserService currentUserService;
    private final InventoryService inventoryService;

    /**
     * Adds or updates commodities in the ADMIN store of a specific branch.
     * <p>
     * For each commodity in the input list:
     * - If a balance already exists for the branch, its quantity is increased.
     * - Otherwise a new balance is created (linked to the branch's ADMIN {@link Store}).
     *
     * @param commodities a list of CommodityWithDetailsDao objects containing commodity and delivery info
     * @param branchID    the ID of the branch where the commodities should be stored
     * @throws NotFoundException if the branch with the given ID does not exist
     */
    @Transactional
    public void addCommoditiesToStore(List<CommodityWithDetailsDao> commodities, Long branchID)
            throws NotFoundException {
        addCommoditiesToStore(commodities, branchID, LedgerContext.unspecified());
    }

    /** As above, recording why the stock arrived. */
    @Transactional
    public void addCommoditiesToStore(List<CommodityWithDetailsDao> commodities, Long branchID,
                                      LedgerContext context) throws NotFoundException {
        Branch branch = branchService.findBranch(branchID);
        Store adminStore = resolveAdminStore(branch);

        // Delegates to the inventory ledger rather than adjusting balances here. This used to be a
        // second, parallel implementation of the same credit, which is why goods receiving could
        // drift from every other stock movement without anything noticing: receiving simply was not
        // going through the service that is supposed to be the single authority over balances.
        for (CommodityWithDetailsDao dao : commodities) {
            inventoryService.creditConsumable(adminStore, dao.getCommodity(), dao.getDeliveredQuantity(), context);
        }
    }

    /**
     * Fetches all balances for a given branch, including the commodity and branch details.
     *
     * @param branchId the ID of the branch to fetch store details for
     * @return list of StoreBalance entities with commodity and branch eagerly loaded
     * @throws NotFoundException if no balances are found for the branch
     */
    /**
     * All (non-courier) balances a branch holds.
     *
     * <p>Returns an empty list when the branch holds nothing. A branch with no stock is a normal,
     * expected state — a newly opened one, or one that has issued everything out — and answering a
     * 404 made it look like the branch or its store did not exist.
     */
    @Transactional(readOnly = true)
    public List<StoreBalance> fetchStoreDetailsPerBranch(Long branchId) {
        return storeBalanceRepository.findAllByBranchIdWithCommodityAndBranch(branchId);
    }

    @Transactional
    /**
     * The store balance listing. Every filter is optional; the store pages fix store type and
     * category from their own chrome and leave the rest to the user.
     *
     * @param stockName partial, case-insensitive match on the commodity's name. Blank is treated as
     *                  absent, so clearing the search box widens the listing rather than asking the
     *                  database for rows whose name contains the empty string.
     */
    public Page<StoreBalance> fetchStoreDetailsPerBranchPerAssetType(
            Long branchId, Long assetTypeId, StoreType storeType, String stockName,
            String stockLevel, Pageable pageable) {
        String name = (stockName == null || stockName.isBlank()) ? null : stockName.trim();
        String level = normaliseStockLevel(stockLevel);
        return storeBalanceRepository.search(branchId, assetTypeId, storeType, name, level,
                withStableOrder(pageable));
    }

    /**
     * Accepts only the three levels the query understands; anything else is treated as no filter.
     *
     * <p>Rejecting silently rather than throwing because an unrecognised value can only come from a
     * stale client, and returning the unfiltered list is the same thing that happened before the
     * filter existed. A hard failure would take the page down over a query string.
     */
    private static String normaliseStockLevel(String stockLevel) {
        if (stockLevel == null || stockLevel.isBlank()) return null;
        String level = stockLevel.trim().toLowerCase();
        return switch (level) {
            case "low", "adequate", "unmonitored" -> level;
            default -> null;
        };
    }

    /**
     * Appends {@code id} as a tie-break to whatever ordering the caller asked for.
     *
     * <p>This listing had no ordering at all, so MySQL was free to return ties in a different order
     * per query — and with OFFSET/LIMIT that means the same row appears on two pages while another
     * appears on none. The same fault was fixed in {@code AssetSearchDao} and {@code RequestSearchDao};
     * this was the third listing carrying it.
     */
    private static Pageable withStableOrder(Pageable pageable) {
        if (pageable == null) return null;
        Sort sort = pageable.getSort();
        boolean alreadyTieBroken = sort.stream()
                .anyMatch(order -> "id".equals(order.getProperty()));
        if (alreadyTieBroken) return pageable;

        Sort stable = sort.isSorted()
                ? sort.and(Sort.by(Sort.Direction.DESC, "id"))
                : Sort.by(Sort.Direction.DESC, "id");
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), stable);
    }


    // reduceCommoditiesFromStore was removed here: it had no callers, and it was a third
    // implementation of "take stock out of a store" alongside InventoryService.debitConsumable —
    // with different validation. Debits belong to the ledger; use InventoryService.debitConsumable.

    public AssetTypeSummaryView findCommoditiesInStockByAssetType() {
        return storeBalanceRepository.findAssetTypeSummary("Stationery");
    }

    public Map<String, Object> getCurrentMonthStationeryForBranch() {
        Long branchId = currentUserService.getCurrentUser().getBranch().getId();
        List<CommoditySummary> summaries = storeBalanceRepository.getStationeryCommoditiesByCurrentMonthByBranch(branchId);

        Map<String, Integer> commodities = new HashMap<>();
        for (CommoditySummary summary : summaries) {
            commodities.put(summary.getCommodityName(), summary.getTotalQuantity());
        }

        String monthName = LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", monthName);
        result.put("commodities", commodities);

        return result;
    }

    /** Resolves the ADMIN store for a branch id, loading the branch first. */
    @Transactional
    public Store resolveAdminStore(Long branchId) throws NotFoundException {
        return resolveAdminStore(branchService.findBranch(branchId));
    }

    /**
     * Returns the branch's location-level ADMIN {@link Store}, creating it if absent. The store
     * seeder normally creates these at startup; this guards against a branch added afterward.
     */
    @Transactional
    public Store resolveAdminStore(Branch branch) {
        return storeRepository
                .findByLocation_IdAndStoreTypeAndDepartmentIsNull(branch.getId(), StoreType.ADMIN)
                .orElseGet(() -> storeRepository.save(Store.builder()
                        .location(branch)
                        .storeType(StoreType.ADMIN)
                        .name(branch.getName() + " Admin Store")
                        .active(true)
                        .build()));
    }
}
