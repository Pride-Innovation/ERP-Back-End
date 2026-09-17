package pride.bank.erp.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalance;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only inventory visibility for Head Office administrators (CLAUDE.md §11) and per-department
 * allocations (§12): cross-location balances, the stores directory, low-stock monitoring, and the
 * serialized assets held by a store (e.g. the IT / Disposal stores). No mutations happen here.
 */
@Service
@RequiredArgsConstructor
public class InventoryReportService {

    private final StoreBalanceRepository storeBalanceRepository;
    private final StoreRepository storeRepository;
    private final AssetRepository assetRepository;
    private final BranchRepository branchRepository;

    /**
     * All consumable balances <b>on hand</b> across every location (centralized visibility).
     *
     * <p>Excludes courier custody: those balances are pinned to Head Office by the schema, so
     * including them both double-counted goods that are still in a van and attributed every
     * in-transit unit in the country to Head Office. Use {@link #inTransitBalances()} to see them.
     */
    @Transactional(readOnly = true)
    public List<StoreBalanceView> allBalances() {
        return storeBalanceRepository.findAllOnHand().stream().map(this::toBalanceView).toList();
    }

    @Transactional(readOnly = true)
    public List<StoreBalanceView> balancesByLocation(Long locationId) {
        return storeBalanceRepository.findOnHandByLocation(locationId).stream().map(this::toBalanceView).toList();
    }

    /**
     * Every branch's stock position in one call: item lines, units on hand, low-stock lines and
     * assets held. The store landing page previously assembled this by querying each branch
     * separately, which cost one HTTP round trip per branch on every load.
     */
    @Transactional(readOnly = true)
    public List<BranchStoreOverview> branchOverview() {
        Map<Long, Object[]> balanceStats = new HashMap<>();
        for (Object[] row : storeBalanceRepository.summariseByBranch()) {
            if (row[0] != null) balanceStats.put(((Number) row[0]).longValue(), row);
        }

        Map<Long, Long> assetsByBranch = new HashMap<>();
        for (Object[] row : assetRepository.countAssetsInStoreByBranch()) {
            if (row[0] != null) assetsByBranch.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        return branchRepository.findAll().stream()
                .map(branch -> {
                    Object[] stats = balanceStats.get(branch.getId());
                    return BranchStoreOverview.builder()
                            .branchId(branch.getId())
                            .branchName(branch.getName())
                            .regionName(branch.getRegion() != null ? branch.getRegion().getName() : null)
                            .itemLines(stats == null ? 0 : ((Number) stats[1]).intValue())
                            .totalQuantity(stats == null ? 0L : ((Number) stats[2]).longValue())
                            .lowStockLines(stats == null || stats[3] == null ? 0 : ((Number) stats[3]).intValue())
                            .assetsHeld(assetsByBranch.getOrDefault(branch.getId(), 0L))
                            .build();
                })
                .toList();
    }

    /** Stock currently held by couriers between two stores — in transit, not available anywhere. */
    @Transactional(readOnly = true)
    public List<StoreBalanceView> inTransitBalances() {
        return storeBalanceRepository.findInTransit().stream().map(this::toBalanceView).toList();
    }

    @Transactional(readOnly = true)
    public List<StoreBalanceView> balancesByStore(Long storeId) {
        return storeBalanceRepository.findByStore_Id(storeId).stream().map(this::toBalanceView).toList();
    }

    @Transactional(readOnly = true)
    public List<StoreBalanceView> balancesByDepartment(Long departmentId) {
        return storeBalanceRepository.findByStore_Department_Id(departmentId).stream().map(this::toBalanceView).toList();
    }

    /**
     * Assets scoped by position in the organisation — branch, department and/or unit.
     *
     * <p>Department and unit come from the person holding the asset rather than from a store, since
     * only departments that genuinely operate a store have one, and units never do.
     */
    @Transactional(readOnly = true)
    public List<Asset> assetsByOrganisationScope(Long branchId, Long departmentId, Long unitId) {
        return assetRepository.findByOrganisationScope(branchId, departmentId, unitId);
    }

    /** Everything a given person is currently holding. */
    @Transactional(readOnly = true)
    public List<Asset> assetsHeldBy(Long userId) {
        return assetRepository.findByAssignedTo_IdAndDisposedFalse(userId);
    }

    @Transactional(readOnly = true)
    public List<StoreBalanceView> balancesByStoreType(StoreType storeType) {
        return storeBalanceRepository.findByStore_StoreType(storeType).stream().map(this::toBalanceView).toList();
    }

    /**
     * Low-stock monitoring (§11): every balance whose on-hand is at or below its own configured
     * reorder threshold ({@code minLevel > 0 && quantity <= minLevel}). Per-commodity thresholds
     * replace the old single global cut-off.
     */
    @Transactional(readOnly = true)
    public List<StoreBalanceView> lowStock() {
        return storeBalanceRepository.findLowStockOnHand().stream().map(this::toBalanceView).toList();
    }

    /** Sets a balance's reorder threshold (0 clears it) and returns the updated view. */
    @Transactional
    public StoreBalanceView setMinLevel(Long balanceId, int minLevel) {
        StoreBalance balance = storeBalanceRepository.findById(balanceId)
                .orElseThrow(() -> new IllegalArgumentException("Store balance " + balanceId + " not found"));
        balance.setMinLevel(Math.max(0, minLevel));
        return toBalanceView(storeBalanceRepository.save(balance));
    }

    /** The stores directory, optionally filtered by location, store type or department. */
    @Transactional(readOnly = true)
    public List<StoreView> stores(Long locationId, StoreType storeType, Long departmentId) {
        List<Store> stores;
        if (departmentId != null) {
            stores = storeRepository.findByDepartment_Id(departmentId);
        } else if (locationId != null) {
            stores = storeRepository.findByLocation_Id(locationId);
        } else if (storeType != null) {
            stores = storeRepository.findByStoreType(storeType);
        } else {
            stores = storeRepository.findAll();
        }
        return stores.stream()
                .filter(s -> storeType == null || s.getStoreType() == storeType)
                .map(this::toStoreView)
                .toList();
    }

    /** Serialized assets currently held by a store — e.g. items in the IT or Disposal store. */
    @Transactional(readOnly = true)
    public List<Asset> assetsInStore(Long storeId) {
        return assetRepository.findByCurrentStore_Id(storeId);
    }

    @Transactional(readOnly = true)
    public List<Asset> assetsByStoreType(StoreType storeType) {
        return assetRepository.findByCurrentStore_StoreType(storeType);
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    private StoreBalanceView toBalanceView(StoreBalance b) {
        Store store = b.getStore();
        return StoreBalanceView.builder()
                .balanceId(b.getId())
                .storeId(store != null ? store.getId() : null)
                .storeName(store != null ? store.getName() : null)
                .storeType(store != null ? store.getStoreType() : null)
                .locationId(b.getBranch() != null ? b.getBranch().getId() : null)
                .locationName(b.getBranch() != null ? b.getBranch().getName() : null)
                .departmentId(store != null && store.getDepartment() != null ? store.getDepartment().getId() : null)
                .departmentName(store != null && store.getDepartment() != null ? store.getDepartment().getName() : null)
                .commodityId(b.getCommodity() != null ? b.getCommodity().getId() : null)
                .commodityName(b.getCommodity() != null ? b.getCommodity().getName() : null)
                .assetTypeName(b.getCommodity() != null && b.getCommodity().getAssetType() != null
                        ? b.getCommodity().getAssetType().getName() : null)
                .quantity(b.getQuantity())
                .minLevel(b.getMinLevel())
                .lowStock(b.getMinLevel() > 0 && b.getQuantity() <= b.getMinLevel())
                .build();
    }

    private StoreView toStoreView(Store s) {
        return StoreView.builder()
                .id(s.getId())
                .name(s.getName())
                .storeType(s.getStoreType())
                .locationId(s.getLocation() != null ? s.getLocation().getId() : null)
                .locationName(s.getLocation() != null ? s.getLocation().getName() : null)
                .departmentId(s.getDepartment() != null ? s.getDepartment().getId() : null)
                .departmentName(s.getDepartment() != null ? s.getDepartment().getName() : null)
                .active(s.isActive())
                .build();
    }
}
