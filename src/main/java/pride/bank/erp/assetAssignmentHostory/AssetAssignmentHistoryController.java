package pride.bank.erp.assetAssignmentHostory;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.helper.Constants;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class AssetAssignmentHistoryController {
    private final AssetAssignmentHistoryService assetAssignmentHistoryService;

    @GetMapping("/assignment-history")
    public ResponseEntity<Page<AssetAssignmentHistory>> findAllAssetAssignmentHistory(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = ASCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) Long assetId
    ) {

        /*
         *  TO DO
         *  Filter by asset id,
         */
        Page<AssetAssignmentHistory> response = assetAssignmentHistoryService.findAllAssetAssignmentHistory(pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
