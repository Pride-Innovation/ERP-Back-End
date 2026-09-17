/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * customize them via the UI in production; these values exist so QA can demo the
 * end-to-end request → workflow → fulfilment flow without manual setup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssetCategoryFeatureSeeder {

    private final AssetTypeRepository assetTypeRepository;

    /*
     * Instance fields, not static — and that distinction is the whole reason this shape exists.
     *
     * `@Value` is applied by Spring when it populates a **bean instance**; a static field belongs to
     * the class, not to any instance, so Spring never touches it and does not warn that it declined
     * to. Worse, a `static final` map is built at class-load time, before the container exists, so
     * even if the injection worked the map would already have been assembled from nulls.
     *
     * That combination was tried here and the application stopped starting: `Map.ofEntries` rejects a
     * null value, so class initialization threw `ExceptionInInitializerError` and the whole context
     * failed to load. Loud, and therefore the lucky outcome — a `HashMap` tolerates nulls, and every
     * category would have been seeded with a null fulfilment mailbox instead, surfacing much later as
     * requests routing nowhere.
     *
     * `BranchHeadOfficeSeeder` has the working shape for the same kind of value, and this now matches
     * it. `StaticValueInjectionTest` fails the build if `@Value` ever lands on a static field again.
     */
    @Value("${application.head.office.infra-email}")
    private String infraEmail;

    @Value("${application.head.office.admin-email}")
    private String adminEmail;

    /**
     * The categories Infra fulfils. Everything else goes to Admin.
     *
     * <h2>Why a set and a fallback rather than the twelve-entry map this replaced</h2>
     * That map sent <b>one</b> category to Infra and the other eleven to Admin, and then fell back to
     * Admin for anything unlisted — so it spelled out eleven identical answers plus a default that
     * repeated them. Stating the exception instead is the same behavior for every category the map
     * named, the same behavior for every category it did not, and it means a category added in
     * Settings tomorrow is routed rather than quietly missing an entry.
     *
     * <p>Kept static because it is a fact about the bank, not about the environment. The addresses are
     * the part that changes per environment, and those are the injected fields above.
     */
    private static final Set<String> INFRA_FULFILLED_CATEGORIES = Set.of("Computers");

    /**
     * Category-name → default custom attributes (only seeded when the category currently has none).
     */
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
            // tracksAssets is deliberately NOT backfilled here: its durable-category default is
            // applied once by AssetTypeCommoditySeeder when a category row is first created, and
            // from then on the flag belongs to the admin toggle (Settings → Asset Categories).
            applyDefaultAttributes(assetType);
            assetTypeRepository.save(assetType);
        }
        log.info("Backfilled owner-group emails / sample custom attributes for {} asset categories", all.size());
    }

    private void applyOwnerGroupEmail(AssetType assetType) {
        if (assetType.getOwnerGroupEmail() != null && !assetType.getOwnerGroupEmail().isBlank()) {
            return;
        }
        String email = ownerEmailFor(assetType.getName());
        if (email == null) {
            // Left unset rather than written blank. The seeder only fills fields that are empty, so
            // an unset one is picked up on the next startup once the address is configured; a blank
            // one would read as "already seeded" and never be revisited.
            return;
        }
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
    ) {
    }

    /**
     * Which mailbox fulfils a category, from the environment's configured addresses.
     *
     * <p>Returns null when the address is not configured, and the caller skips the field rather than
     * writing a blank one — an empty {@code ownerGroupEmail} is worse than an absent one, because the
     * seeder only fills fields that are unset and would therefore never revisit it. Same defensive
     * shape as {@code BranchHeadOfficeSeeder}, which logs and skips rather than seeding an empty name.
     */
    private String ownerEmailFor(String categoryName) {
        String email = INFRA_FULFILLED_CATEGORIES.contains(categoryName) ? infraEmail : adminEmail;

        if (email == null || email.isBlank()) {
            log.warn("No fulfilment mailbox configured for category '{}' — set INFRA_EMAIL and "
                    + "ADMIN_EMAIL. Leaving its owner group email unset.", categoryName);
            return null;
        }
        return email.trim();
    }
}
