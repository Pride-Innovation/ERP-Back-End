/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetType;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssetTypeService {
    private final AssetTypeRepository assetTypeRepository;

    public Page<AssetType> fetchAllAssetTypes(Pageable pageable, String name) {
        if (StringUtils.hasText(name)) {
            return assetTypeRepository.findAllByNameContainingIgnoreCase(name.trim(), pageable);
        }
        return assetTypeRepository.findAll(pageable);
    }

    public AssetType createAssetType(AssetType request) {
        if (!StringUtils.hasText(request.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset category name is required");
        }
        if (assetTypeRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An asset category with the name '" + request.getName() + "' already exists");
        }
        if (StringUtils.hasText(request.getShortCode()) &&
                assetTypeRepository.existsByShortCodeIgnoreCase(request.getShortCode().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An asset category with the short code '" + request.getShortCode() + "' already exists");
        }
        AssetType assetType = AssetType.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .shortCode(StringUtils.hasText(request.getShortCode()) ? request.getShortCode().trim().toUpperCase() : null)
                .build();
        return assetTypeRepository.save(assetType);
    }

    public AssetType findOneAssetType(Long id) throws NotFoundException {
        return assetTypeRepository.findById(id).orElseThrow(
                () -> new NotFoundException("Asset category with ID " + id + " not found"));
    }

    public AssetType updateAssetType(AssetType request, Long id) throws NotFoundException {
        AssetType assetType = findOneAssetType(id);
        if (!StringUtils.hasText(request.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset category name is required");
        }
        if (assetTypeRepository.existsByNameIgnoreCaseAndIdNot(request.getName().trim(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An asset category with the name '" + request.getName() + "' already exists");
        }
        if (StringUtils.hasText(request.getShortCode()) &&
                assetTypeRepository.existsByShortCodeIgnoreCaseAndIdNot(request.getShortCode().trim(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An asset category with the short code '" + request.getShortCode() + "' already exists");
        }
        assetType.setName(request.getName().trim());
        assetType.setDescription(request.getDescription());
        assetType.setShortCode(StringUtils.hasText(request.getShortCode()) ? request.getShortCode().trim().toUpperCase() : null);
        return assetTypeRepository.save(assetType);
    }

    public DeleteResponse deleteAssetType(Long id) throws NotFoundException {
        if (!assetTypeRepository.existsById(id)) {
            throw new NotFoundException("Asset category with ID " + id + " not found");
        }
        try {
            assetTypeRepository.deleteById(id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete this asset category. It may still be linked to commodities or assets.");
        }
    }

    public AssetType updateFieldConfig(Long id, Map<String, String> fieldConfig) throws NotFoundException {
        AssetType assetType = findOneAssetType(id);
        assetType.setFieldConfig(fieldConfig);
        return assetTypeRepository.save(assetType);
    }
}

