package pride.bank.erp.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.AssetTypeSummaryView;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchService;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.NotFoundException;
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
        Branch branch = branchService.findBranch(branchID);
        Store adminStore = resolveAdminStore(branch);

        for (CommodityWithDetailsDao dao : commodities) {
            Commodity commodity = dao.getCommodity();

            // Store-scoped, not branch-scoped: a branch can hold the same commodity in more than
            // one store (its ADMIN store plus a courier custody row anchored at Head Office), so a
            // branch-only lookup could return more than one row. Stocking always targets the ADMIN store.
            StoreBalance balance = storeBalanceRepository
                    .findByStore_IdAndCommodity_Id(adminStore.getId(), commodity.getId())
                    .orElse(null);

            if (balance != null) {
                balance.setQuantity(balance.getQuantity() + dao.getDeliveredQuantity());
                if (balance.getStore() == null) {
                    balance.setStore(adminStore);
                }
            } else {
                balance = StoreBalance.builder()
                        .branch(branch)
                        .store(adminStore)
                        .commodity(commodity)
                        .quantity(dao.getDeliveredQuantity())
                        .build();
            }

            storeBalanceRepository.save(balance);
        }
    }

    /**
     * Fetches all balances for a given branch, including the commodity and branch details.
     *
     * @param branchId the ID of the branch to fetch store details for
     * @return list of StoreBalance entities with commodity and branch eagerly loaded
     * @throws NotFoundException if no balances are found for the branch
     */
    @Transactional(readOnly = true)
    public List<StoreBalance> fetchStoreDetailsPerBranch(Long branchId)
            throws NotFoundException {

        List<StoreBalance> storeList = storeBalanceRepository.findAllByBranchIdWithCommodityAndBranch(branchId);

        if (storeList.isEmpty()) {
            throw new NotFoundException(
                    String.format("Store details for branch with id %s are not found", branchId)
            );
        }

        return storeList;
    }

    @Transactional
    public Page<StoreBalance> fetchStoreDetailsPerBranchPerAssetType(
            Long branchId, Long assetTypeId, StoreType storeType, Pageable pageable) {
        // Store-scoped: the Admin / IT / Disposal pages each see only the lines their own store
        // container holds — an item cannot appear on all three pages at once.
        if (storeType != null) {
            return assetTypeId == null
                    ? storeBalanceRepository.findByBranchIdAndStore_StoreType(branchId, storeType, pageable)
                    : storeBalanceRepository.findByBranchIdAndStore_StoreTypeAndCommodity_AssetTypeId(
                            branchId, storeType, assetTypeId, pageable);
        }
        // No category filter → every balance line the branch holds (the store landing page
        // probes per-branch line counts this way).
        if (assetTypeId == null) {
            return storeBalanceRepository.findByBranchId(branchId, pageable);
        }
        return storeBalanceRepository
                .findByBranchIdAndCommodity_AssetTypeId(branchId, assetTypeId, pageable);
    }


    /**
     * Reduces the quantity of commodities in the ADMIN store of a specific branch.
     * <p>
     * For each commodity in the input list:
     * - If a balance exists for the branch and has sufficient quantity, its quantity is decreased.
     * - If no balance exists or quantity is insufficient, an exception is thrown.
     *
     * @param commodities a list of CommodityWithDetailsDao objects containing commodity and quantity to reduce
     * @param branchID    the ID of the branch where the commodities should be reduced
     * @throws NotFoundException        if the branch or commodity is not found
     * @throws IllegalArgumentException if there is insufficient quantity to reduce
     */
    @Transactional
    public void reduceCommoditiesFromStore(List<CommodityWithDetailsDao> commodities, Long branchID)
            throws NotFoundException, IllegalArgumentException {
        Branch branch = branchService.findBranch(branchID);
        Store adminStore = resolveAdminStore(branch);

        for (CommodityWithDetailsDao dao : commodities) {
            Commodity commodity = dao.getCommodity();

            // Store-scoped: reduce from the branch's ADMIN store (see addCommoditiesToStore).
            StoreBalance balance = storeBalanceRepository
                    .findByStore_IdAndCommodity_Id(adminStore.getId(), commodity.getId())
                    .orElseThrow(() -> new NotFoundException(String.format("Commodity %s not found in store for the store", commodity.getName())));

            int currentQuantity = balance.getQuantity();
            int quantityToReduce = dao.getDeliveredQuantity();

            if (currentQuantity < quantityToReduce) {
                throw new IllegalArgumentException("Insufficient quantity for commodity: " + commodity.getName());
            }

            balance.setQuantity(currentQuantity - quantityToReduce);
            storeBalanceRepository.save(balance);
        }
    }

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
