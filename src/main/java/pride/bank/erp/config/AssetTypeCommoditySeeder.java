/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.unitofmeasure.UnitOfMeasureService;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AssetTypeCommoditySeeder {
    private static final String ASSET_TYPES_RESOURCE = "assetTypes.json";

    private final AssetTypeRepository assetTypeRepository;
    private final CommodityRepository commodityRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void seedAssetTypesAndCommodities() {
        try {
            ClassPathResource resource = new ClassPathResource(ASSET_TYPES_RESOURCE);
            List<AssetTypeSeedNode> assetTypes = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });

            seedAssetTypesAndCommodities(assetTypes);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to seed asset types and commodities from " + ASSET_TYPES_RESOURCE, exception);
        }
    }

    void seedAssetTypesAndCommodities(List<AssetTypeSeedNode> assetTypes) {
        for (AssetTypeSeedNode assetTypeNode : assetTypes) {
            AssetType assetType = findOrCreateAssetType(assetTypeNode);
            seedCommodities(assetType, assetTypeNode.getCommodities());
        }
    }

    private AssetType findOrCreateAssetType(AssetTypeSeedNode node) {
        Optional<AssetType> existingAssetType = findExistingAssetType(node);

        if (existingAssetType.isPresent()) {
            AssetType assetType = existingAssetType.get();
            boolean requiresUpdate = false;

            if (!Objects.equals(assetType.getName(), node.getName())) {
                assetType.setName(node.getName());
                requiresUpdate = true;
            }

            if (!Objects.equals(assetType.getDescription(), node.getDescription())) {
                assetType.setDescription(node.getDescription());
                requiresUpdate = true;
            }

            if (!Objects.equals(assetType.getShortCode(), node.getShortCode())) {
                assetType.setShortCode(node.getShortCode());
                requiresUpdate = true;
            }

            if (requiresUpdate) {
                return assetTypeRepository.save(assetType);
            }

            return assetType;
        }

        return assetTypeRepository.save(
                AssetType.builder()
                        .name(node.getName())
                        .description(node.getDescription())
                        .shortCode(node.getShortCode())
                        // Durable-category default, applied ONLY when the row is first created so
                        // an admin's later toggle (Settings → Asset Categories) survives restarts.
                        .tracksAssets(ASSET_TRACKING_CATEGORIES.contains(node.getName()))
                        .build()
        );
    }

    /**
     * Durable categories whose stocked items should auto-create trackable assets on a
     * brand-new database. Consumables (Stationery, Cleaning, Healthcare, Kitchen, etc.)
     * are intentionally excluded. After creation the flag is owned by the admin UI and
     * is never touched by seeding again.
     */
    private static final java.util.Set<String> ASSET_TRACKING_CATEGORIES = java.util.Set.of(
            "Computers",
            "Equipment",
            "Furniture",
            "Vehicle/Fleet",
            "Security",
            "Financial & Banking Equipment"
    );

    private Optional<AssetType> findExistingAssetType(AssetTypeSeedNode node) {
        if (node.getShortCode() != null && !node.getShortCode().isBlank()) {
            Optional<AssetType> assetTypeByShortCode = assetTypeRepository.findByShortCode(node.getShortCode());
            if (assetTypeByShortCode.isPresent()) {
                return assetTypeByShortCode;
            }
        }

        if (node.getName() != null && !node.getName().isBlank()) {
            return assetTypeRepository.findByNameIgnoreCase(node.getName());
        }

        return Optional.empty();
    }

    private void seedCommodities(AssetType assetType, List<CommoditySeedNode> commodities) {
        if (commodities == null) {
            return;
        }

        Set<String> seenKeys = new HashSet<>();

        for (CommoditySeedNode commodityNode : commodities) {
            String commodityKey = buildCommodityKey(commodityNode);
            if (!seenKeys.add(commodityKey)) {
                continue;
            }

            findOrCreateCommodity(assetType, commodityNode);
        }
    }

    /*
     * Found by name within its category - never by unit as well.
     *
     * The lookup used to include the unit, and an existing commodity's unit was overwritten from this
     * file. With units managed in Settings → Units of Measure that would be wrong twice: a commodity
     * whose unit an administrator changed would no longer match, so every restart would create a
     * second commodity with the file's unit beside it; and one that did match would have its unit put
     * back. The unit in this file is now only the starting value for a commodity it creates.
     */
    private Commodity findOrCreateCommodity(AssetType assetType, CommoditySeedNode node) {
        Optional<Commodity> existingCommodity = commodityRepository
                .findAllByNameIgnoreCaseAndAssetType_IdOrderByIdAsc(node.getName(), assetType.getId())
                .stream().findFirst();

        if (existingCommodity.isPresent()) {
            Commodity commodity = existingCommodity.get();
            boolean requiresUpdate = false;

            if (!Objects.equals(commodity.getName(), node.getName())) {
                commodity.setName(node.getName());
                requiresUpdate = true;
            }

            if (commodity.getAssetType() == null || !Objects.equals(commodity.getAssetType().getId(), assetType.getId())) {
                commodity.setAssetType(assetType);
                requiresUpdate = true;
            }

            if (requiresUpdate) {
                return commodityRepository.save(commodity);
            }

            return commodity;
        }

        return commodityRepository.save(
                Commodity.builder()
                        .name(node.getName())
                        .groupName(UnitOfMeasureService.normalise(node.getGroupName()))
                        .assetType(assetType)
                        .build()
        );
    }

    private String buildCommodityKey(CommoditySeedNode node) {
        // By name alone, matching the lookup above.
        return node.getName() == null ? "" : node.getName().trim().toLowerCase();
    }
}