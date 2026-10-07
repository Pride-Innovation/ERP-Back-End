/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.commodity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.stockCommodity.StockCommodity;
import pride.bank.erp.supplier.Supplier;

import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"requestCommodities", "stockCommodities", "assetType", "suppliers", "stockingBranches"})
@ToString(exclude = {"requestCommodities", "stockCommodities", "assetType", "suppliers", "stockingBranches"})
public class Commodity implements pride.bank.erp.stocking.StockingRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String groupName; // Like pieces, dozens, etc

    // This is optional but can be useful to easily access commodities associated with a request
    @OneToMany(mappedBy = "commodity", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @Builder.Default
    private Set<RequestCommodity> requestCommodities = new HashSet<>();

    // This is optional but can be useful to easily access commodities associated with a stock
    @OneToMany(mappedBy = "commodity", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @Builder.Default
    private Set<StockCommodity> stockCommodities = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_type_id")
    private AssetType assetType;

    @ManyToMany(mappedBy = "commodities", fetch = FetchType.EAGER)
    @JsonIgnoreProperties({"commodities", "stocks"})
    @Builder.Default
    private Set<Supplier> suppliers = new HashSet<>();

    // ── Where this commodity may be stocked (V28) - see StockingRuleService ─────────────────────

    /**
     * False (the default): follow the category's rule, and the three fields below are ignored.
     * True: this commodity's own rule wins - the exception a category rule cannot express, such as
     * letting branches stock toner while printers stay at Head Office.
     */
    @Column(name = "stocking_override", columnDefinition = "BIT NOT NULL DEFAULT 0")
    @Builder.Default
    private boolean stockingOverride = false;

    @Column(name = "stock_at_head_office", columnDefinition = "BIT NOT NULL DEFAULT 1")
    @Builder.Default
    private boolean stockAtHeadOffice = true;

    @Column(name = "stock_at_all_branches", columnDefinition = "BIT NOT NULL DEFAULT 0")
    @Builder.Default
    private boolean stockAtAllBranches = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "commodity_stocking_branch",
            joinColumns = @JoinColumn(name = "commodity_id"),
            inverseJoinColumns = @JoinColumn(name = "branch_id"))
    @JsonIgnore
    @Builder.Default
    private Set<pride.bank.erp.branch.Branch> stockingBranches = new HashSet<>();

    /** The specific branches as ids, which is all a client needs - not the branch records. */
    @com.fasterxml.jackson.annotation.JsonProperty("stockingBranchIds")
    public java.util.List<Long> getStockingBranchIds() {
        return stockingBranches == null ? java.util.List.of()
                : stockingBranches.stream().map(pride.bank.erp.branch.Branch::getId).sorted().toList();
    }
}
