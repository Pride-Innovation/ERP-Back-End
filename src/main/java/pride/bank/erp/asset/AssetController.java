/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
            @RequestParam(required = false, defaultValue = "false") Boolean disposed,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String assignedTo // Name of the assigned to user, can first name, last name or other name
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
                disposed,
                model,
                assignedTo
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
    public ResponseEntity<AssetDAO> updateAsset(
            @PathVariable Long id,
            @RequestBody AssetDTO assetDTO
    ) throws NotFoundException {
        AssetDAO response = assetService.updateAsset(assetDTO, id);
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

    @PostMapping("/assets/{assetID}")
    public ResponseEntity<AssetDAO> disposeAsset(@PathVariable Long assetID)
            throws NotFoundException {
        AssetDAO response = assetService.disposeAsset(assetID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/assets/reassign/{assetID}")
    public ResponseEntity<AssetDAO> reassignAsset(
            @PathVariable Long assetID,
            @RequestBody ReassignAsset reassignAsset
    )
            throws NotFoundException {
        AssetDAO response = assetService.reassignAsset(assetID, reassignAsset);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping(value = "assets/image/{assetID}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetDAO> uploadAssetImage(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @PathVariable Long assetID
    ) throws IOException, NotFoundException {
        AssetDAO response = assetService.uploadAssetImage(file, assetID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("assets/remove/image/{assetID}")
    public ResponseEntity<AssetDAO> removeAssetImage(
            @PathVariable Long assetID
    ) throws NotFoundException {
        AssetDAO response = assetService.removeAssetImage(assetID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("assets/store/{assetID}")
    public ResponseEntity<AssetDAO> sendAssetToStore(
            @PathVariable Long assetID
    ) throws NotFoundException {
        AssetDAO response = assetService.sendAssetToStore(assetID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("assets/bulk-insert")
    public ResponseEntity<Map<String, Object>> bulkInsertAsset(
            @RequestParam("assets") String assets,
            @RequestParam("assetTypeID") Long assetTypeID
    ) throws JsonProcessingException, NotFoundException {

        ObjectMapper objectMapper = new ObjectMapper();
        List<BulkAssetDTO> assetList = objectMapper.readValue(assets,
                objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, BulkAssetDTO.class));

        Map<String, Object> response = assetService.bulkInsertAsset(assetList, assetTypeID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
