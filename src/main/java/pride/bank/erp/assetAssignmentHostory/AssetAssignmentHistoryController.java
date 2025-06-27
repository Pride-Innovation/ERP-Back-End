package pride.bank.erp.assetAssignmentHostory;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.helper.Constants;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class AssetAssignmentHistoryController {
    private final AssetAssignmentHistoryService assetAssignmentHistoryService;

    public ResponseEntity<Page<AssetAssignmentHistory>> findAllAssetAssignmentHistory(
            Pageable pageable
    ) {
        Page<AssetAssignmentHistory> response = assetAssignmentHistoryService.findAllAssetAssignmentHistory(pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
