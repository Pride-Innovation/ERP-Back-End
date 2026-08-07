package pride.bank.erp.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.config.BranchScopeService;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.user.User;

import java.util.List;
import java.util.Map;

/**
 * Read-only inventory visibility endpoints (CLAUDE.md §11-12): centralized cross-location balances,
 * the stores directory, low-stock monitoring, department allocations, and store-held assets.
 */
@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class InventoryReportController {

    private final InventoryReportService reportService;
    private final CurrentUserService currentUserService;
    private final BranchScopeService branchScope;

    /**
     * Consumable balances. With no params returns every location's balances; otherwise filters by
     * one of locationId, storeId, departmentId or storeType.
     */
    @GetMapping("/inventory/balances")
    public ResponseEntity<List<StoreBalanceView>> balances(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) StoreType storeType
    ) {
        // Resolve first: a branch user asking for "balances" with no location gets their own, while
        // one naming somebody else's branch is refused outright.
        Long scopedLocation = branchScope.resolve(locationId);

        List<StoreBalanceView> result;
        if (storeId != null) {
            result = reportService.balancesByStore(storeId);
        } else if (departmentId != null) {
            result = reportService.balancesByDepartment(departmentId);
        } else if (scopedLocation != null) {
            result = reportService.balancesByLocation(scopedLocation);
        } else if (storeType != null) {
            result = reportService.balancesByStoreType(storeType);
        } else {
            result = reportService.allBalances();
        }

        // The store- and department-scoped branches above resolve a container rather than a branch,
        // so filter their output to what the caller is allowed to see.
        if (!branchScope.canViewAllBranches()) {
            Long own = branchScope.currentUserBranchId();
            result = result.stream()
                    .filter(b -> b.getLocationId() == null || b.getLocationId().equals(own))
                    .toList();
        }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /** Low-stock monitoring: balances at or below their own configured reorder threshold. */
    @GetMapping("/inventory/low-stock")
    public ResponseEntity<List<StoreBalanceView>> lowStock() {
        return new ResponseEntity<>(scopeBalances(reportService.lowStock()), HttpStatus.OK);
    }

    /**
     * Assets scoped by position in the organisation. Any of the three may be omitted.
     * Department and unit are resolved from the holder, since only some departments operate a store
     * and units never do.
     */
    @GetMapping("/inventory/assets/scoped")
    public ResponseEntity<List<Asset>> assetsByScope(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long unitId
    ) {
        return new ResponseEntity<>(
                reportService.assetsByOrganisationScope(branchScope.resolve(branchId), departmentId, unitId),
                HttpStatus.OK);
    }

    /** What the signed-in user is currently holding — their "My Items" view. */
    @GetMapping("/inventory/my-items")
    public ResponseEntity<List<Asset>> myItems() {
        User current = currentUserService.getCurrentUser();
        if (current == null) {
            return new ResponseEntity<>(List.of(), HttpStatus.OK);
        }
        return new ResponseEntity<>(reportService.assetsHeldBy(current.getId()), HttpStatus.OK);
    }

    /**
     * Every branch's stock position — item lines, units, low-stock lines and assets held — in one
     * call, so the store landing page no longer issues a request per branch.
     */
    @GetMapping("/inventory/branch-overview")
    public ResponseEntity<List<BranchStoreOverview>> branchOverview() {
        List<BranchStoreOverview> rows = reportService.branchOverview();
        if (!branchScope.canViewAllBranches()) {
            Long own = branchScope.currentUserBranchId();
            rows = rows.stream().filter(r -> r.getBranchId() != null && r.getBranchId().equals(own)).toList();
        }
        return new ResponseEntity<>(rows, HttpStatus.OK);
    }

    /**
     * Stock currently in a courier's custody between two stores. Deliberately separate from
     * {@code /inventory/balances}, which reports only what is on hand — in-transit units are not
     * available anywhere and must not be counted as branch stock.
     */
    @GetMapping("/inventory/in-transit")
    public ResponseEntity<List<StoreBalanceView>> inTransit() {
        // Not branch-filtered: custody stores belong to a courier, not to a branch, so scoping them
        // by location would hide everything from everyone. Restricted to cross-branch viewers.
        if (!branchScope.canViewAllBranches()) {
            return new ResponseEntity<>(List.of(), HttpStatus.OK);
        }
        return new ResponseEntity<>(reportService.inTransitBalances(), HttpStatus.OK);
    }

    /** Sets (or clears, with 0) a balance's reorder threshold. */
    @PreAuthorize("hasAuthority('" + Constants.UPDATE_STORE + "')")
    @PutMapping("/inventory/balances/{balanceId}/min-level")
    public ResponseEntity<StoreBalanceView> setMinLevel(
            @PathVariable Long balanceId,
            @RequestBody Map<String, Integer> body
    ) {
        int minLevel = (body != null && body.get("minLevel") != null) ? body.get("minLevel") : 0;
        return new ResponseEntity<>(reportService.setMinLevel(balanceId, minLevel), HttpStatus.OK);
    }

    /** The stores directory, optionally filtered by location, store type or department. */
    @GetMapping("/inventory/stores")
    public ResponseEntity<List<StoreView>> stores(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) StoreType storeType,
            @RequestParam(required = false) Long departmentId
    ) {
        List<StoreView> stores = reportService.stores(branchScope.resolve(locationId), storeType, departmentId);
        if (!branchScope.canViewAllBranches()) {
            Long own = branchScope.currentUserBranchId();
            stores = stores.stream()
                    .filter(s -> s.getLocationId() == null || s.getLocationId().equals(own))
                    .toList();
        }
        return new ResponseEntity<>(stores, HttpStatus.OK);
    }

    /** Restricts a balance listing to what the caller is allowed to see. */
    private List<StoreBalanceView> scopeBalances(List<StoreBalanceView> balances) {
        if (branchScope.canViewAllBranches()) return balances;
        Long own = branchScope.currentUserBranchId();
        return balances.stream()
                .filter(b -> b.getLocationId() == null || b.getLocationId().equals(own))
                .toList();
    }

    /** Serialized assets currently held by a store (e.g. items in the IT or Disposal store). */
    @GetMapping("/inventory/stores/{storeId}/assets")
    public ResponseEntity<List<Asset>> assetsInStore(@PathVariable Long storeId) {
        return new ResponseEntity<>(scopeAssets(reportService.assetsInStore(storeId)), HttpStatus.OK);
    }

    /** Serialized assets held by any store of a given type across locations. */
    @GetMapping("/inventory/assets")
    public ResponseEntity<List<Asset>> assetsByStoreType(@RequestParam StoreType storeType) {
        return new ResponseEntity<>(scopeAssets(reportService.assetsByStoreType(storeType)), HttpStatus.OK);
    }

    /**
     * Loanable pool stock: assets flagged as temporary-pool, unassigned, and sitting in a store.
     *
     * <p>Pool assets live in the IT store or the Head Office Admin store depending on their
     * category's repair routing, so both are searched. Filtered here rather than in the browser —
     * the picker used to fetch every IT and Admin asset and filter client-side, which silently
     * returned an incomplete pool as soon as the listing exceeded one page.
     */
    @GetMapping("/inventory/assets/temporary-pool")
    public ResponseEntity<List<Asset>> temporaryPoolAssets() {
        List<Asset> assets = java.util.stream.Stream
                .concat(reportService.assetsByStoreType(StoreType.IT).stream(),
                        reportService.assetsByStoreType(StoreType.ADMIN).stream())
                .filter(Asset::isTemporaryPool)
                .filter(a -> a.getAssignedTo() == null)
                .toList();
        return new ResponseEntity<>(scopeAssets(assets), HttpStatus.OK);
    }

    /**
     * Assets that may be written off, with the age judgement already made.
     *
     * <p>Its own endpoint rather than a flag on {@code /inventory/assets} because the disposal picker
     * asks a different question: not "what is in this store" but "what may I dispose, and does this
     * one need a reason". Answering that here keeps the rule in one place — the picker previously
     * listed IT-store assets only, with no notion of eligibility, so a week-old laptop looked exactly
     * like a five-year-old one.
     *
     * <p>Only IT and ADMIN stores are considered, matching the same restriction enforced on write in
     * {@code RepairMovementService#disposeAsset}.
     */
    @GetMapping("/inventory/assets/disposable")
    public ResponseEntity<List<DisposalCandidateView>> disposalCandidates() {
        List<Asset> assets = java.util.stream.Stream
                .concat(reportService.assetsByStoreType(StoreType.IT).stream(),
                        reportService.assetsByStoreType(StoreType.ADMIN).stream())
                .toList();
        return new ResponseEntity<>(
                scopeAssets(assets).stream().map(DisposalCandidateView::of).toList(),
                HttpStatus.OK);
    }

    /**
     * Restricts an asset listing to the caller's branch.
     *
     * <p>Scoped on the store actually holding the asset rather than {@code asset.branch}, so an item
     * in a courier's custody does not leak into a branch listing on the strength of a denormalised
     * field.
     */
    private List<Asset> scopeAssets(List<Asset> assets) {
        if (branchScope.canViewAllBranches()) return assets;
        Long own = branchScope.currentUserBranchId();
        return assets.stream()
                .filter(a -> {
                    Long holdingBranch = (a.getCurrentStore() != null && a.getCurrentStore().getLocation() != null)
                            ? a.getCurrentStore().getLocation().getId()
                            : (a.getBranch() != null ? a.getBranch().getId() : null);
                    return holdingBranch != null && holdingBranch.equals(own);
                })
                .toList();
    }
}
