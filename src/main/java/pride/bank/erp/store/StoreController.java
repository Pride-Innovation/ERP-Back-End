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

    @GetMapping("/store/{branchId}")
    public ResponseEntity<List<StoreBalance>> findStoreListByBranch(@PathVariable Long branchId) {
        // Named explicitly in the path, so refuse rather than silently substitute the caller's own
        // branch — that would show one branch's figures under another's name.
        branchScope.requireAccessTo(branchId);
        List<StoreBalance> response = storeService.fetchStoreDetailsPerBranch(branchId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

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

    @GetMapping("/stationery-report")
    public ResponseEntity<AssetTypeSummaryView> findCommoditiesInStockByAssetType() {
        return new ResponseEntity<>(storeService.findCommoditiesInStockByAssetType(), HttpStatus.OK);
    }

    @GetMapping("/stationery-report/branch")
    public ResponseEntity<Map<String, Object>> getCurrentMonthStationeryForBranch() {
        return new ResponseEntity<>(storeService.getCurrentMonthStationeryForBranch(), HttpStatus.OK);
    }
}
