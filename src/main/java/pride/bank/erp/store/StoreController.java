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
        branchScope.requireAccessTo(request.getLocationId());
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
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber
    ) {
        branchScope.requireAccessTo(branchId);
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<StoreBalance> response = storeService.fetchStoreDetailsPerBranchPerAssetType(
                branchId,
                assetTypeId,
                storeType,
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
