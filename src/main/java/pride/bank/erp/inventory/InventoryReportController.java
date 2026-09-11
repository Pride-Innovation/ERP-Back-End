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
    private final pride.bank.erp.movement.MovementRepository movementRepository;

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
    /*
     * The store directory is needed by five unrelated jobs, so it is guarded by a disjunction rather
     * than one permission.
     *
     * It was open to any signed-in user — it fell through every matcher to
     * `.anyRequest().authenticated()`, and INVENTORY_ROUTE (/api/v1/stocks/**) covers nothing under
     * /api/v1/inventory/**, a name that implies a coverage its value does not provide.
     *
     * Naming a single permission would break a real page: the assets page's "Receive into store"
     * picker is used by someone holding RECEIVE_ASSET_IN_STORE who may well have no store
     * permissions, and the movement builder by someone holding CREATE_MOVEMENT. Each authority below
     * is a legitimate reason to need to know what stores exist; together they still exclude a
     * signed-in user with none of them, which is the gap that was actually open.
     */
    @PreAuthorize("hasAnyAuthority('" + Constants.READ_STORE + "', '" + Constants.READ_INVENTORY
            + "', '" + Constants.RECEIVE_ASSET_IN_STORE + "', '" + Constants.CREATE_MOVEMENT
            + "', '" + Constants.PERFORM_STOCK_TAKE + "')")
    @GetMapping("/inventory/stores")
    public ResponseEntity<List<StoreView>> stores(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) StoreType storeType,
            @RequestParam(required = false) Long departmentId
    ) {
        /*
         * Named branch: refused if it is not theirs. No branch named: NOT substituted.
         *
         * This used BranchScopeService#resolve, which quietly replaces a null location with the
         * caller's own branch. For the directory that is fatal rather than merely narrow: asking for
         * "the IT store" with no location became "IT stores at Gulu", and there are none — the one
         * IT store in the bank stands at Head Office. The query returned nothing, so the filter
         * below had nothing to let through however carefully it was written, and a branch user's
         * IT and Disposal pages showed an empty shelf while Head Office saw the same store full.
         *
         * Substituting is right for a *balance* listing, where null means "my branch's figures".
         * It is wrong here, because a store the caller may legitimately see does not stand at their
         * branch. The narrowing belongs after the query, where "or it belongs to everybody" can be
         * said.
         */
        if (locationId != null) {
            branchScope.requireAccessTo(locationId);
        }
        List<StoreView> stores = reportService.stores(locationId, storeType, departmentId);
        if (!branchScope.canViewAllBranches()) {
            Long own = branchScope.currentUserBranchId();
            /*
             * A branch sees its own stores — and the two that belong to everybody.
             *
             * Filtering this list on the store's location alone was the mistake the store plan
             * warns about in as many words: the IT and Disposal stores are bank-wide singletons
             * that stand at Head Office, so every branch was filtered out of seeing they exist.
             * The visible symptom was an empty asset panel on /store/it and /store/disposal for
             * every branch user — the page asked which store to read, got nothing back, and
             * rendered no rows. The invisible one is worse: these two stores are where repairs and
             * write-offs route, and anything picking a destination from this list could not offer
             * them.
             *
             * Letting the rows through discloses nothing — that the bank has an IT store is not a
             * secret, and the two are seeded by name. What must stay scoped is what is *inside*
             * them, and that is handled separately and by the right key: `scopeBalances` on the
             * balance's owning branch, `scopeAssets` on the asset's branch or the branch it came
             * from. Directory open, contents scoped.
             */
            stores = stores.stream()
                    .filter(s -> s.getLocationId() == null
                            || s.getLocationId().equals(own)
                            || isBankWide(s))
                    .toList();
        }
        return new ResponseEntity<>(stores, HttpStatus.OK);
    }

    /**
     * The stores that belong to the whole bank rather than to one branch.
     *
     * <p>One IT store and one Disposal store exist for the entire organisation — {@code StoreSeeder}
     * asserts both singletons on startup — and every branch's repairs and write-offs route through
     * them. They are nobody's branch store, so no branch may be filtered out of seeing them.
     */
    private static boolean isBankWide(StoreView store) {
        return store.getStoreType() == StoreType.IT || store.getStoreType() == StoreType.DISPOSAL;
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
    /*
     * The store directory is needed by five unrelated jobs, so it is guarded by a disjunction rather
     * than one permission.
     *
     * It was open to any signed-in user — it fell through every matcher to
     * `.anyRequest().authenticated()`, and INVENTORY_ROUTE (/api/v1/stocks/**) covers nothing under
     * /api/v1/inventory/**, a name that implies a coverage its value does not provide.
     *
     * Naming a single permission would break a real page: the assets page's "Receive into store"
     * picker is used by someone holding RECEIVE_ASSET_IN_STORE who may well have no store
     * permissions, and the movement builder by someone holding CREATE_MOVEMENT. Each authority below
     * is a legitimate reason to need to know what stores exist; together they still exclude a
     * signed-in user with none of them, which is the gap that was actually open.
     */
    @PreAuthorize("hasAnyAuthority('" + Constants.READ_STORE + "', '" + Constants.READ_INVENTORY
            + "', '" + Constants.RECEIVE_ASSET_IN_STORE + "', '" + Constants.CREATE_MOVEMENT
            + "', '" + Constants.PERFORM_STOCK_TAKE + "')")
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
    /**
     * Narrows a list of stored assets to the ones the caller's branch may see.
     *
     * <h2>An asset in a shared store is not Head Office's</h2>
     * Most stores are branch-owned. Two are not: the IT and Disposal stores are singletons for the
     * whole bank, held at Head Office, and every branch's repairs and write-offs route into them.
     *
     * <p>That makes {@code asset.branch} the wrong question to ask on its own here. It records where
     * the asset <em>is</em>, and {@code relocateAsset} sets it to the destination store's location on
     * transfer — deliberately, because Head Office really does have custody while the item is on its
     * bench, and the return leg depends on it. So a Gulu laptop sent for repair reads Head Office, and
     * Gulu could not see its own machine while Head Office saw everyone's.
     *
     * <p>The branch that owns it survives on the movement that took it away, as
     * {@code originLocation} — the same column the return leg aims at. A caller therefore reaches an
     * asset if <b>either</b> end is theirs: where it sits now, or where it came from. That mirrors the
     * multi-ended reach a movement already gets from {@code requireCanViewAny}, and for the same
     * reason — a record can legitimately belong to two places at once.
     *
     * <p><b>Visibility only.</b> Nothing here changes what a transfer writes. Preserving the origin on
     * {@code asset.branch} instead would have been the tidier-looking fix and a much larger one: that
     * field also resolves which Admin store a repaired asset goes home to, and which branch the repair
     * guard checks, so it would have altered the movement flows rather than the view of them.
     */
    private List<Asset> scopeAssets(List<Asset> assets) {
        if (branchScope.canViewAllBranches()) return assets;
        Long own = branchScope.currentUserBranchId();
        if (own == null) return List.of();

        Map<Long, Long> originByAsset = originLocations(assets);

        return assets.stream()
                .filter(a -> {
                    Long holdingBranch = a.getBranch() != null ? a.getBranch().getId() : null;
                    if (own.equals(holdingBranch)) return true;
                    // Away from home: visible to the branch that sent it, not only to its host.
                    return own.equals(originByAsset.get(a.getId()));
                })
                .toList();
    }

    /**
     * The branch each asset was last sent from, in one query rather than one per row.
     *
     * <p>Only the newest movement counts, so an asset repaired twice is not claimed by whoever owned
     * it two repairs ago. The query returns newest first and the first row per asset wins.
     */
    private Map<Long, Long> originLocations(List<Asset> assets) {
        List<Long> ids = assets.stream().map(Asset::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();

        Map<Long, Long> newestFirst = new java.util.HashMap<>();
        for (Object[] row : movementRepository.findOriginLocationsForAssets(ids)) {
            newestFirst.putIfAbsent((Long) row[0], (Long) row[1]);
        }
        return newestFirst;
    }
}
