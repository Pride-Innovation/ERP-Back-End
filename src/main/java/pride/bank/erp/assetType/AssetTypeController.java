/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.util.List;
import java.util.Map;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
@Tag(name = "Asset Categories", description = "Manage asset categories – creation, retrieval, update and removal")
@SecurityRequirement(name = "bearerAuth")
public class AssetTypeController {
    public final AssetTypeService assetTypeService;

    @Operation(summary = "List asset categories", description = "Returns a paginated list of asset categories, optionally filtered by name.")
    @GetMapping("/asset-types")
    public ResponseEntity<Page<AssetTypeDTO>> fetchAllAssetTypes(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "name") String sortBy,
            @RequestParam(required = false, defaultValue = ASCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false) String name
    ) {
        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<AssetTypeDTO> response = assetTypeService.fetchAllAssetTypes(pageable, name);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(summary = "Create an asset category")
    @PostMapping("/asset-types")
    public ResponseEntity<AssetTypeDTO> createAssetType(@RequestBody AssetTypeDTO assetType) {
        AssetTypeDTO response = assetTypeService.createAssetType(assetType);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Update an asset category")
    @PutMapping("/asset-types/{id}")
    public ResponseEntity<AssetTypeDTO> updateAssetType(@PathVariable Long id, @RequestBody AssetTypeDTO assetType)
            throws NotFoundException {
        AssetTypeDTO response = assetTypeService.updateAssetType(assetType, id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(summary = "Delete an asset category")
    @DeleteMapping("/asset-types/{id}")
    public ResponseEntity<DeleteResponse> deleteAssetType(@PathVariable Long id) throws NotFoundException {
        DeleteResponse response = assetTypeService.deleteAssetType(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(summary = "Get a single asset category by ID")
    @GetMapping("/asset-types/{id}")
    public ResponseEntity<AssetTypeDTO> getOneAssetType(@PathVariable Long id)
            throws NotFoundException {
        return new ResponseEntity<>(assetTypeService.getOneAssetType(id), HttpStatus.OK);
    }

    @Operation(
            summary = "Configure field visibility for an asset category",
            description = "Sets each form field to 'required', 'optional', or 'hidden' for this asset type."
    )
    @PutMapping("/asset-types/{id}/field-config")
    public ResponseEntity<AssetTypeDTO> updateFieldConfig(
            @PathVariable Long id,
            @RequestBody Map<String, String> fieldConfig
    ) throws NotFoundException {
        AssetTypeDTO response = assetTypeService.updateFieldConfig(id, fieldConfig);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Replace the custom attributes for an asset category",
            description = "Replaces the asset category's custom attributes with the supplied array. " +
                    "Order in the payload determines display order (1..n)."
    )
    @PutMapping("/asset-types/{id}/custom-attributes")
    public ResponseEntity<List<AssetTypeCustomAttributeDTO>> updateCustomAttributes(
            @PathVariable Long id,
            @RequestBody List<AssetTypeCustomAttributeDTO> attributes
    ) throws NotFoundException {
        List<AssetTypeCustomAttributeDTO> response = assetTypeService.updateCustomAttributes(id, attributes);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
