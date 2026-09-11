package pride.bank.erp.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.asset.AssetTypeSummaryView;
import pride.bank.erp.config.BranchScopeService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class StoreController {
    private final StoreService storeService;
    private final StoreAdminService storeAdminService;
    private final BranchScopeService branchScope;

    /**
     * Creates a department-level store. Location-level Admin stores are seeded per branch, and IT /
     * Disposal are organisation-wide singletons, so neither is creatable here.
     */
    @PreAuthorize("hasAuthority('" + Constants.CREATE_STORE + "')")
    @PostMapping("/stores")
    public ResponseEntity<Store> createDepartmentStore(@RequestBody StoreAdminDTO request)
            throws NotFoundException {
        // A write, so it asks the manage question: reporting on another branch's stock is not a
        // licence to reorganise its stores.
        branchScope.requireCanChange(request.getLocationId());
        return new ResponseEntity<>(storeAdminService.createDepartmentStore(request), HttpStatus.CREATED);
    }

    /** Activates or deactivates a department store. Refused while the store still holds stock. */
    @PreAuthorize("hasAuthority('" + Constants.UPDATE_STORE + "')")
    @PutMapping("/stores/{storeId}/active")
    public ResponseEntity<Store> setActive(
            @PathVariable Long storeId,
            @RequestParam boolean active
    ) throws NotFoundException {
        return new ResponseEntity<>(storeAdminService.setActive(storeId, active), HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.UPDATE_STORE + "')")
    @PutMapping("/stores/{storeId}/name")
    public ResponseEntity<Store> rename(
            @PathVariable Long storeId,
            @RequestBody StoreAdminDTO request
    ) throws NotFoundException {
        return new ResponseEntity<>(storeAdminService.rename(storeId, request.getName()), HttpStatus.OK);
    }

    /*
     * Both branch reads were scoped but not gated: neither carried a @PreAuthorize, so they fell
     * through to `.anyRequest().authenticated()` and the rule was "any login, your own branch".
     *
     * The page in front of them already requires READ_STORE (`StoreRoutes` gates /store/admin,
     * /store/it and /store/disposal on it), so the endpoint and the route now ask for the same
     * thing — the rule this codebase has broken three times by letting them differ. READ_INVENTORY
     * rides alongside for the same reason the store directory and the stationery reports take a
     * disjunction: several unrelated jobs read a branch's stock position, and naming one permission
     * refuses one of them.
     */
    @PreAuthorize("hasAnyAuthority('" + Constants.READ_STORE + "', '" + Constants.READ_INVENTORY + "')")
    @GetMapping("/store/{branchId}")
    public ResponseEntity<List<StoreBalance>> findStoreListByBranch(@PathVariable Long branchId) {
        // Named explicitly in the path, so refuse rather than silently substitute the caller's own
        // branch — that would show one branch's figures under another's name.
        branchScope.requireAccessTo(branchId);
        List<StoreBalance> response = storeService.fetchStoreDetailsPerBranch(branchId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PreAuthorize("hasAnyAuthority('" + Constants.READ_STORE + "', '" + Constants.READ_INVENTORY + "')")
    @GetMapping("/store")
    public ResponseEntity<Page<StoreBalance>> fetchStoreDetailsPerBranchPerAssetType(
            @RequestParam Long branchId,
            @RequestParam(required = false) Long assetTypeId,
            @RequestParam(required = false) StoreType storeType,
            // Declared, because Spring silently drops a parameter no endpoint names — a filter the
            // client sends to a signature that does not list it looks like it worked and returns the
            // unfiltered list. This box existed on the page for exactly that reason.
            @RequestParam(required = false) String stockName,
            /*
             * low / adequate / unmonitored, applied against each line's own reorder threshold.
             *
             * This is a real server-side filter now because the threshold is real: `minLevel` is set
             * per balance through PUT /inventory/balances/{id}/min-level and is what the low-stock
             * monitor already reads. The store page's Status column used to invent its own bands
             * (under 5 / 5-10 / over 10) in the browser, so the two screens could disagree about the
             * same commodity.
             */
            @RequestParam(required = false) String stockLevel,
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber
    ) {
        branchScope.requireAccessTo(branchId);
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<StoreBalance> response = storeService.fetchStoreDetailsPerBranchPerAssetType(
                branchId,
                assetTypeId,
                storeType,
                stockName,
                stockLevel,
                pageable
        );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * How many balance lines each category holds — the store page's "Inventory by category" strip.
     *
     * <h2>Why this endpoint exists</h2>
     * The strip had no endpoint of its own, so it called {@code GET /store} <b>once per category</b>
     * with {@code pageSize=1} and read {@code totalElements} off each reply. Twelve categories is
     * twelve round trips to draw one row of tiles — and when the branch is outside the caller's
     * reach, twelve refusals instead of one.
     *
     * <p>Mapped above {@code /store/{branchId}} in the file but not in Spring's eyes: a literal path
     * segment always beats a template, so "category-counts" cannot be mistaken for a branch id.
     *
     * @return {@code {assetTypeId: count}}, omitting categories with nothing in this store
     */
    @PreAuthorize("hasAnyAuthority('" + Constants.READ_STORE + "', '" + Constants.READ_INVENTORY + "')")
    @GetMapping("/store/category-counts")
    public ResponseEntity<Map<Long, Long>> countStoreLinesByCategory(
            @RequestParam Long branchId,
            @RequestParam(required = false) StoreType storeType
    ) {
        // Same guard as the listing beside it, and for the same reason: naming another branch is a
        // deliberate act, so it is refused rather than quietly rewritten to the caller's own.
        branchScope.requireAccessTo(branchId);
        return new ResponseEntity<>(storeService.countByAssetType(branchId, storeType), HttpStatus.OK);
    }

    /*
     * Both stationery reports were unguarded — no @PreAuthorize, so they fell through to
     * `.anyRequest().authenticated()` and any signed-in user could read them. The bank-wide one is
     * now scoped in the service as well as gated here; the branch one already read only the caller's
     * own branch and needed the gate rather than the scope.
     *
     * Guarded by the same disjunction the store directory uses: several unrelated jobs legitimately
     * read stock figures, and naming a single permission would refuse one of them.
     */
    @PreAuthorize("hasAnyAuthority('" + Constants.READ_STORE + "', '" + Constants.READ_INVENTORY + "')")
    @GetMapping("/stationery-report")
    public ResponseEntity<AssetTypeSummaryView> findCommoditiesInStockByAssetType() {
        return new ResponseEntity<>(storeService.findCommoditiesInStockByAssetType(), HttpStatus.OK);
    }

    @PreAuthorize("hasAnyAuthority('" + Constants.READ_STORE + "', '" + Constants.READ_INVENTORY + "')")
    @GetMapping("/stationery-report/branch")
    public ResponseEntity<Map<String, Object>> getCurrentMonthStationeryForBranch() {
        return new ResponseEntity<>(storeService.getCurrentMonthStationeryForBranch(), HttpStatus.OK);
    }
}
