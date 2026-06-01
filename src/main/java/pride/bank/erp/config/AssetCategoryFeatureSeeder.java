/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeCustomAttribute;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.assetType.AttributeDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backfills the Phase A fields ({@code ownerGroupEmail} + sample {@code customAttributes})
 * for existing asset categories. Idempotent — only sets fields that are unset.
 *
 * <p>The defaults here are intentionally generic. Pride's admins are expected to
 * customise them via the UI in production; these values exist so QA can demo the
 * end-to-end request → workflow → fulfilment flow without manual setup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssetCategoryFeatureSeeder {

    private final AssetTypeRepository assetTypeRepository;

    /** Category-name → default fulfilment mailbox. Unknown categories fall back to a generic mailbox. */
    private static final Map<String, String> CATEGORY_OWNER_EMAILS = Map.ofEntries(
            Map.entry("Computers", "it-infra@bank.example"),
            Map.entry("Furniture", "admin@bank.example"),
            Map.entry("Stationery", "admin@bank.example"),
            Map.entry("Equipment", "facilities@bank.example"),
            Map.entry("Vehicle/Fleet", "fleet@bank.example"),
            Map.entry("Security", "security@bank.example"),
            Map.entry("Cleaning & Hygiene", "facilities@bank.example"),
            Map.entry("Healthcare", "facilities@bank.example"),
            Map.entry("Printing & Branding", "marketing@bank.example"),
            Map.entry("Building & Construction", "facilities@bank.example"),
            Map.entry("Financial & Banking Equipment", "operations@bank.example"),
            Map.entry("Kitchen & Groceries", "admin@bank.example")
    );
    private static final String DEFAULT_OWNER_EMAIL = "fulfilment-default@bank.example";

    /** Category-name → default custom attributes (only seeded when the category currently has none). */
    private static final Map<String, List<AttributeSeed>> CATEGORY_ATTRIBUTES = Map.of(
            "Computers", List.of(
                    new AttributeSeed("warranty_months", "Warranty (months)", AttributeDataType.NUMBER, null,
                            false, "Vendor-supplied warranty period"),
                    new AttributeSeed("form_factor", "Form Factor", AttributeDataType.SELECT,
                            List.of("Laptop", "Desktop", "Workstation"), false, null),
                    new AttributeSeed("intended_user", "Intended User", AttributeDataType.TEXT, null,
                            false, "Staff member who will use this device")
            ),
            "Furniture", List.of(
                    new AttributeSeed("placement_location", "Placement Location", AttributeDataType.TEXT, null,
                            true, null),
                    new AttributeSeed("colour_preference", "Colour Preference", AttributeDataType.TEXT, null,
                            false, "Optional, where applicable")
            )
    );

    @Transactional
    public void seedFeatures() {
        List<AssetType> all = assetTypeRepository.findAll();
        for (AssetType assetType : all) {
            applyOwnerGroupEmail(assetType);
            applyDefaultAttributes(assetType);
            assetTypeRepository.save(assetType);
        }
        log.info("Backfilled owner-group emails / sample custom attributes for {} asset categories", all.size());
    }

    private void applyOwnerGroupEmail(AssetType assetType) {
        if (assetType.getOwnerGroupEmail() != null && !assetType.getOwnerGroupEmail().isBlank()) {
            return;
        }
        String email = CATEGORY_OWNER_EMAILS.getOrDefault(assetType.getName(), DEFAULT_OWNER_EMAIL);
        assetType.setOwnerGroupEmail(email);
    }

    private void applyDefaultAttributes(AssetType assetType) {
        List<AttributeSeed> seeds = CATEGORY_ATTRIBUTES.get(assetType.getName());
        if (seeds == null || seeds.isEmpty()) {
            return;
        }
        if (assetType.getCustomAttributes() != null && !assetType.getCustomAttributes().isEmpty()) {
            // Don't clobber existing attribute config
            return;
        }
        if (assetType.getCustomAttributes() == null) {
            assetType.setCustomAttributes(new java.util.ArrayList<>());
        }

        Set<String> seen = new HashSet<>();
        int order = 1;
        for (AttributeSeed seed : seeds) {
            if (!seen.add(seed.key)) {
                continue;
            }
            AssetTypeCustomAttribute attr = AssetTypeCustomAttribute.builder()
                    .assetType(assetType)
                    .key(seed.key)
                    .label(seed.label)
                    .dataType(seed.dataType)
                    .options(seed.options)
                    .required(seed.required)
                    .displayOrder(order++)
                    .helperText(seed.helperText)
                    .build();
            assetType.getCustomAttributes().add(attr);
        }
    }

    private record AttributeSeed(
            String key,
            String label,
            AttributeDataType dataType,
            List<String> options,
            boolean required,
            String helperText
    ) {}
}
