/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.util.List;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class AssetController {
    private final AssetService assetService;

    @GetMapping("/assets")
    public ResponseEntity<Page<AssetDAO>> fetchAllAssets(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = DESCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String assetName,
            @RequestParam Long assetTypeId,
            @RequestParam(required = false) Long assetStatusId,
            @RequestParam(required = false) Long commodityId,
            @RequestParam(required = false) String engravedNumber,
            @RequestParam(required = false) Long assignedToId,
            @RequestParam(required = false, defaultValue = "false") Boolean disposed
    ) {
        Page<AssetDAO> response = assetService.fetchAllAssets(
                pageSize,
                sortDirection,
                pageNumber,
                sortBy,
                assetName,
                assetTypeId,
                assetStatusId,
                commodityId,
                engravedNumber,
                assignedToId,
                disposed
        );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/assets")
    public ResponseEntity<Asset> createAsset(@RequestBody AssetDTO assetDTO)
            throws NotFoundException {
        Asset asset = assetService.createAsset(assetDTO);
        return new ResponseEntity<>(asset, HttpStatus.CREATED);
    }

    @GetMapping("/assets/{id}")
    public ResponseEntity<AssetDAO> getSingleAsset(@PathVariable Long id)
            throws NotFoundException {
        AssetDAO asset = assetService.fetchSingleAsset(id);
        return new ResponseEntity<>(asset, HttpStatus.OK);
    }

    @PutMapping("/assets/{id}")
    public ResponseEntity<Asset> updateAsset(
            @PathVariable Long id,
            @RequestBody AssetDTO assetDTO
    ) throws NotFoundException {
        Asset response = assetService.updateAsset(assetDTO, id);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/dashboard-asset-report")
    public ResponseEntity<List<AssetTypeSummaryView>> listDashboardAssetReport() {
        return new ResponseEntity<>(assetService.listDashboardAssetReport(), HttpStatus.OK);
    }

    @GetMapping("/assets/statistics")
    public List<AssetTypeStatsProjection> getAssetStatistics() {
        return assetService.getAssetTypeStatistics();
    }

    @GetMapping("/assets/my-assets")
    public List<AssetGroupDTO> getMyAssets() {
        return assetService.getAssetsGroupedByType();
    }

    @GetMapping("/assets/tag-name")
    public ResponseEntity<Asset> findAssetByTagName(@RequestParam String tagName)
            throws NotFoundException {
        return ResponseEntity.ok(assetService.findAssetByTagName(tagName));
    }

    @DeleteMapping("/assets/{assetID}")
    public ResponseEntity<Asset> disposeAsset(@PathVariable Long assetID)
            throws NotFoundException {
        return new ResponseEntity<>(
                assetService.disposeAsset(assetID),
                HttpStatus.CREATED
        );
    }
}
