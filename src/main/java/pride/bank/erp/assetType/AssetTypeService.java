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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AssetTypeService {
    private static final Pattern ATTRIBUTE_KEY_PATTERN = Pattern.compile("^[a-z0-9_]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AssetTypeRepository assetTypeRepository;

    public Page<AssetTypeDTO> fetchAllAssetTypes(Pageable pageable, String name) {
        Page<AssetType> page = StringUtils.hasText(name)
                ? assetTypeRepository.findAllByNameContainingIgnoreCase(name.trim(), pageable)
                : assetTypeRepository.findAll(pageable);
        return page.map(this::toDTO);
    }

    public AssetTypeDTO createAssetType(AssetTypeDTO request) {
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
        validateOwnerGroupEmail(request.getOwnerGroupEmail());

        AssetType assetType = AssetType.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .shortCode(StringUtils.hasText(request.getShortCode()) ? request.getShortCode().trim().toUpperCase() : null)
                .ownerGroupEmail(normaliseEmail(request.getOwnerGroupEmail()))
                .fieldConfig(request.getFieldConfig())
                .build();
        return toDTO(assetTypeRepository.save(assetType));
    }

    public AssetType findOneAssetType(Long id) throws NotFoundException {
        return assetTypeRepository.findById(id).orElseThrow(
                () -> new NotFoundException("Asset category with ID " + id + " not found"));
    }

    public AssetTypeDTO getOneAssetType(Long id) throws NotFoundException {
        return toDTO(findOneAssetType(id));
    }

    public AssetTypeDTO updateAssetType(AssetTypeDTO request, Long id) throws NotFoundException {
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
        validateOwnerGroupEmail(request.getOwnerGroupEmail());

        assetType.setName(request.getName().trim());
        assetType.setDescription(request.getDescription());
        assetType.setShortCode(StringUtils.hasText(request.getShortCode()) ? request.getShortCode().trim().toUpperCase() : null);
        assetType.setOwnerGroupEmail(normaliseEmail(request.getOwnerGroupEmail()));
        return toDTO(assetTypeRepository.save(assetType));
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

    public AssetTypeDTO updateFieldConfig(Long id, Map<String, String> fieldConfig) throws NotFoundException {
        AssetType assetType = findOneAssetType(id);
        assetType.setFieldConfig(fieldConfig);
        return toDTO(assetTypeRepository.save(assetType));
    }

    /**
     * Replace-semantics: the incoming list fully overwrites the asset type's custom attributes.
     * Order in the payload determines displayOrder (1..n).
     */
    @Transactional
    public List<AssetTypeCustomAttributeDTO> updateCustomAttributes(Long id, List<AssetTypeCustomAttributeDTO> attributes)
            throws NotFoundException {
        AssetType assetType = findOneAssetType(id);
        List<AssetTypeCustomAttributeDTO> safe = attributes == null ? new ArrayList<>() : attributes;
        validateCustomAttributes(safe);

        assetType.getCustomAttributes().clear();
        int order = 1;
        for (AssetTypeCustomAttributeDTO dto : safe) {
            AssetTypeCustomAttribute entity = AssetTypeCustomAttribute.builder()
                    .assetType(assetType)
                    .key(dto.getKey().trim())
                    .label(dto.getLabel().trim())
                    .dataType(dto.getDataType())
                    .options(dto.getDataType() == AttributeDataType.SELECT ? dto.getOptions() : null)
                    .required(dto.isRequired())
                    .displayOrder(order++)
                    .helperText(StringUtils.hasText(dto.getHelperText()) ? dto.getHelperText().trim() : null)
                    .build();
            assetType.getCustomAttributes().add(entity);
        }

        AssetType saved = assetTypeRepository.save(assetType);
        return saved.getCustomAttributes().stream().map(this::toCustomAttributeDTO).toList();
    }

    // ─────────────────────────── helpers ───────────────────────────

    private void validateCustomAttributes(List<AssetTypeCustomAttributeDTO> attributes) {
        Set<String> seenKeys = new HashSet<>();
        for (int i = 0; i < attributes.size(); i++) {
            AssetTypeCustomAttributeDTO a = attributes.get(i);
            String position = "Custom attribute #" + (i + 1);

            if (a == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, position + " is null");
            }
            if (!StringUtils.hasText(a.getKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, position + " is missing 'key'");
            }
            String key = a.getKey().trim();
            if (!ATTRIBUTE_KEY_PATTERN.matcher(key).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        position + " key '" + key + "' is invalid (allowed: lowercase letters, digits, underscore)");
            }
            if (!seenKeys.add(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Duplicate custom attribute key '" + key + "' in payload");
            }
            if (!StringUtils.hasText(a.getLabel())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, position + " is missing 'label'");
            }
            if (a.getDataType() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, position + " is missing 'dataType'");
            }
            if (a.getDataType() == AttributeDataType.SELECT) {
                List<String> opts = a.getOptions();
                if (opts == null || opts.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            position + " (dataType=SELECT) must have a non-empty 'options' array");
                }
                for (String opt : opts) {
                    if (!StringUtils.hasText(opt)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                position + " has a blank option");
                    }
                }
            }
        }
    }

    private void validateOwnerGroupEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ownerGroupEmail '" + email + "' is not a valid email address");
        }
    }

    private String normaliseEmail(String email) {
        return StringUtils.hasText(email) ? email.trim() : null;
    }

    AssetTypeDTO toDTO(AssetType entity) {
        if (entity == null) {
            return null;
        }
        List<AssetTypeCustomAttributeDTO> attrs = entity.getCustomAttributes() == null
                ? new ArrayList<>()
                : entity.getCustomAttributes().stream().map(this::toCustomAttributeDTO).toList();
        return AssetTypeDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .shortCode(entity.getShortCode())
                .ownerGroupEmail(entity.getOwnerGroupEmail())
                .fieldConfig(entity.getFieldConfig())
                .customAttributes(attrs)
                .build();
    }

    AssetTypeCustomAttributeDTO toCustomAttributeDTO(AssetTypeCustomAttribute entity) {
        return AssetTypeCustomAttributeDTO.builder()
                .id(entity.getId())
                .key(entity.getKey())
                .label(entity.getLabel())
                .dataType(entity.getDataType())
                .options(entity.getOptions())
                .required(entity.isRequired())
                .order(entity.getDisplayOrder())
                .helperText(entity.getHelperText())
                .build();
    }
}
