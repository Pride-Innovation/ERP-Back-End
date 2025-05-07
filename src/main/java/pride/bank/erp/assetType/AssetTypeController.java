package pride.bank.erp.assetType;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class AssetTypeController {
    public final AssetTypeService assetTypeService;

    @GetMapping("/asset-types")
    public ResponseEntity<Page<AssetType>> fetchAllAssetTypes(Pageable pageable) {
        Page<AssetType> response = assetTypeService.fetchAllAssetTypes(pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/asset-types")
    public ResponseEntity<AssetType> createAssetType(@RequestBody AssetType assetType) {
        AssetType response = assetTypeService.createAssetType(assetType);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @PutMapping("/asset-types/{id}")
    public ResponseEntity<AssetType> updateAssetType(@PathVariable Long id, @RequestBody AssetType assetType)
            throws NotFoundException {
        AssetType response = assetTypeService.updateAssetType(assetType, id);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @DeleteMapping("/asset-types/{id}")
    public ResponseEntity<DeleteResponse> deleteAssetType(@PathVariable Long id) {
        DeleteResponse response = assetTypeService.deleteAssetType(id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(204));
    }

    @GetMapping("/asset-types/{id}")
    public ResponseEntity<AssetType> getOneAssetType(@PathVariable Long id)
            throws NotFoundException {
        return new ResponseEntity<>(assetTypeService.findOneAssetType(id), HttpStatus.OK);
    }
}

