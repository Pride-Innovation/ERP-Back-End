package pride.bank.erp.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.store.StoreType;

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
        List<StoreBalanceView> result;
        if (storeId != null) {
            result = reportService.balancesByStore(storeId);
        } else if (departmentId != null) {
            result = reportService.balancesByDepartment(departmentId);
        } else if (locationId != null) {
            result = reportService.balancesByLocation(locationId);
        } else if (storeType != null) {
            result = reportService.balancesByStoreType(storeType);
        } else {
            result = reportService.allBalances();
        }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /** Low-stock monitoring: balances at or below their own configured reorder threshold. */
    @GetMapping("/inventory/low-stock")
    public ResponseEntity<List<StoreBalanceView>> lowStock() {
        return new ResponseEntity<>(reportService.lowStock(), HttpStatus.OK);
    }

    /** Sets (or clears, with 0) a balance's reorder threshold. */
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
        return new ResponseEntity<>(reportService.stores(locationId, storeType, departmentId), HttpStatus.OK);
    }

    /** Serialized assets currently held by a store (e.g. items in the IT or Disposal store). */
    @GetMapping("/inventory/stores/{storeId}/assets")
    public ResponseEntity<List<Asset>> assetsInStore(@PathVariable Long storeId) {
        return new ResponseEntity<>(reportService.assetsInStore(storeId), HttpStatus.OK);
    }

    /** Serialized assets held by any store of a given type across locations. */
    @GetMapping("/inventory/assets")
    public ResponseEntity<List<Asset>> assetsByStoreType(@RequestParam StoreType storeType) {
        return new ResponseEntity<>(reportService.assetsByStoreType(storeType), HttpStatus.OK);
    }
}
