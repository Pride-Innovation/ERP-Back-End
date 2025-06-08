package pride.bank.erp.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.util.List;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class StoreController {
    private final StoreService storeService;

    @GetMapping("/store/{branchId}")
    public ResponseEntity<List<Store>> findStoreListByBranch(@PathVariable Long branchId)
            throws NotFoundException {
        List<Store> response = storeService.fetchStoreDetailsPerBranch(branchId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/store/{branchId}/{assetTypeId}")
    public ResponseEntity<Page<Store>> fetchStoreDetailsPerBranchPerAssetType(
            @PathVariable Long branchId,
            @PathVariable Long assetTypeId,
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<Store> response = storeService.fetchStoreDetailsPerBranchPerAssetType(
                branchId,
                assetTypeId,
                pageable
        );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
