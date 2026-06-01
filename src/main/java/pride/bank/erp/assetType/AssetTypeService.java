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
import java.util.HashMap;
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
     *
     * <p>Implemented as a diff/merge against the existing rows (matched by {@code key}) rather than
     * clear-then-rebuild. The naive {@code collection.clear(); add(new)} flow tripped the unique
     * constraint on {@code (asset_type_id, key)} because Hibernate flushes the new INSERTs before
     * the orphan DELETEs — yielding a 409 "Duplicate entry" the moment any attribute kept its key.
     * Merging in place also preserves the original row ids, which is friendlier to history/audits.</p>
     */
    @Transactional
    public List<AssetTypeCustomAttributeDTO> updateCustomAttributes(Long id, List<AssetTypeCustomAttributeDTO> attributes)
            throws NotFoundException {
        AssetType assetType = findOneAssetType(id);
        List<AssetTypeCustomAttributeDTO> safe = attributes == null ? new ArrayList<>() : attributes;
        validateCustomAttributes(safe);

        // Index existing rows by key so we can update in place rather than re-insert.
        Map<String, AssetTypeCustomAttribute> existingByKey = new HashMap<>();
        for (AssetTypeCustomAttribute a : assetType.getCustomAttributes()) {
            existingByKey.put(a.getKey(), a);
        }

        Set<String> incomingKeys = new HashSet<>();
        int order = 1;
        for (AssetTypeCustomAttributeDTO dto : safe) {
            String key = dto.getKey().trim();
            incomingKeys.add(key);
            AssetTypeCustomAttribute entity = existingByKey.get(key);
            if (entity == null) {
                entity = AssetTypeCustomAttribute.builder()
                        .assetType(assetType)
                        .key(key)
                        .build();
                assetType.getCustomAttributes().add(entity);
            }
            entity.setLabel(dto.getLabel().trim());
            entity.setDataType(dto.getDataType());
            entity.setOptions(dto.getDataType() == AttributeDataType.SELECT ? dto.getOptions() : null);
            entity.setRequired(dto.isRequired());
            entity.setDisplayOrder(order++);
            entity.setHelperText(StringUtils.hasText(dto.getHelperText()) ? dto.getHelperText().trim() : null);
        }

        // Drop any rows whose key is no longer in the payload — orphanRemoval will DELETE them.
        assetType.getCustomAttributes().removeIf(a -> !incomingKeys.contains(a.getKey()));

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
