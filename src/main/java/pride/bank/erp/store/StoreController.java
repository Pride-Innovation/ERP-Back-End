package pride.bank.erp.store;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
}
