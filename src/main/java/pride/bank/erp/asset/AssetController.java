package pride.bank.erp.asset;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class AssetController {
    private final AssetService assetService;

    @GetMapping("/assets")
    public ResponseEntity<Page<Asset>> fetchAllAssets(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<Asset> response = assetService.fetchAllAssets(pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/assets")
    public ResponseEntity<Asset> createAsset(@RequestBody AssetDTO assetDTO)
            throws NotFoundException {
        Asset asset = assetService.createAsset(assetDTO);
        return null;
    }
}
