/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetType implements pride.bank.erp.stocking.StockingRule {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String shortCode;

    /**
     * Email group notified to fulfil a request after approvals
     * (e.g. it-infra@bank for Computer, admin@bank for Furniture).
     */
    @Column(name = "owner_group_email", length = 255)
    private String ownerGroupEmail;

    /**
     * Annual reducing-balance depreciation rate (percent) for assets of this category: each year's
     * charge is the opening net book value times this rate.
     * Used to derive each asset's current net book value on read. Null = no depreciation configured.
     */
    @Column(name = "depreciation_rate")
    private Double depreciationRate;

    /**
     * Useful life in months. An asset is flagged ready for disposal once the time since its
     * receipt date reaches this many months. Null/0 = never auto-flagged by age.
     */
    @Column(name = "useful_life_months")
    private Integer usefulLifeMonths;

    /**
     * Whether stocking items of this category should auto-create trackable {@code Asset}
     * records (durable goods such as Computers, Furniture, Vehicles). Consumables
     * (Stationery, Cleaning, Healthcare, etc.) leave this {@code false}.
     *
     * <p>Replaces the old hardcoded category-name check in {@code StockService} so the
     * "stocking creates assets and notifies the owning team" behaviour is configurable
     * per category.
     */
    @Column(name = "tracks_assets", columnDefinition = "BIT NOT NULL DEFAULT 0")
    @Builder.Default
    private boolean tracksAssets = false;

    /**
     * Whether faulty assets of this category can be repaired at all. When {@code false}, a repair
     * transfer short-circuits straight to the Disposal store instead of a workshop/consultant.
     */
    @Column(name = "repairable", columnDefinition = "BIT NOT NULL DEFAULT 1")
    @Builder.Default
    private boolean repairable = true;

    /**
     * Default routing for a faulty asset of this category (IT / ADMIN / EXTERNAL). Overridable at
     * transfer time; null falls back to {@code IT}. Ignored when {@link #repairable} is false.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "repair_destination", length = 20)
    private RepairDestination repairDestination;

    /**
     * Per-field visibility configuration.
     * Each key is a form-field name; each value is one of: "required", "optional", "hidden".
     * Stored as a JSON string in a large-text column.
     */
    // MSSQL (uncomment + comment-out the MySQL line below when switching back):
    // @Column(name = "field_config", columnDefinition = "NVARCHAR(MAX)")
    // MySQL:
    @Column(name = "field_config", columnDefinition = "LONGTEXT")
    @Convert(converter = FieldConfigConverter.class)
    private Map<String, String> fieldConfig;

    @OneToMany(mappedBy = "assetType", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    @JsonIgnore
    @Builder.Default
    private List<AssetTypeCustomAttribute> customAttributes = new ArrayList<>();

    // ── Where this category may be stocked (V28) - see StockingRuleService ──────────────────────
    // Every commodity in the category follows this unless it sets its own. A new category starts at
    // Head Office only, the conservative default: widening is a decision, narrowing a surprise.

    @Column(name = "stock_at_head_office", columnDefinition = "BIT NOT NULL DEFAULT 1")
    @Builder.Default
    private boolean stockAtHeadOffice = true;

    @Column(name = "stock_at_all_branches", columnDefinition = "BIT NOT NULL DEFAULT 0")
    @Builder.Default
    private boolean stockAtAllBranches = false;

    /**
     * Out of JSON (the DTO carries ids) and out of equals/hashCode/toString: Branch's Lombok hash
     * reaches into its region, and hashing through a collection like this is the trap CLAUDE.md
     * records against Title and Region.
     */
    @jakarta.persistence.ManyToMany(fetch = FetchType.EAGER)
    @jakarta.persistence.JoinTable(name = "asset_type_stocking_branch",
            joinColumns = @jakarta.persistence.JoinColumn(name = "asset_type_id"),
            inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "branch_id"))
    @JsonIgnore
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    @Builder.Default
    private java.util.Set<pride.bank.erp.branch.Branch> stockingBranches = new java.util.HashSet<>();

    /**
     * The named branches as ids, for wherever the entity itself is serialised - inside a commodity,
     * which is how the commodity form shows the category rule it would otherwise follow.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("stockingBranchIds")
    public List<Long> getStockingBranchIds() {
        return stockingBranches == null ? List.of()
                : stockingBranches.stream().map(pride.bank.erp.branch.Branch::getId).sorted().toList();
    }
}
