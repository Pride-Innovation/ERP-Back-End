/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.grnReport.GrnReport;
import pride.bank.erp.status.Status;
import pride.bank.erp.stockCommodity.StockCommodity;
import pride.bank.erp.supplier.Supplier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Audited
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String referenceNumber;
    private String name;
    /** Order totals in UGX. See StockCommodity for why these are BigDecimal rather than int. */
    @Column(precision = 19, scale = 4)
    private BigDecimal totalCost;

    @Column(precision = 19, scale = 4)
    private BigDecimal balanceCost;
    private String lpoNumber;
    private String poNumber;
    private String grnNumber;

    // Client-supplied, stable per submission. A unique key means a retried/duplicated
    // create request resolves to the same stock instead of creating a second one
    // (with a second set of assets). NOTE: ddl-auto=update does NOT reliably create the
    // unique index — verify uk on `idempotency_key` on the actual DB after deploy.
    @Column(unique = true)
    private String idempotencyKey;

    // Real business dates for the purchase. `deliveryDate` in particular is the basis for
    // asset depreciation/age — the system previously fell back to createDate (the row's
    // insert time), which is wrong for back-dated stocking.
    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;
    private LocalDateTime invoiceDate;

    // Reason recorded when a partial delivery is closed short (supplier won't deliver the rest).
    private String closeShortReason;

    @Builder.Default
    private boolean canNotBeDeleted = false;
    @Builder.Default
    private Boolean disposed = false;
    private LocalDateTime disposalDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_id")
    @NotAudited
    private Status status;

    /**
     * Out of equals/hashCode/toString - the same lazy-collection cycle as {@code Title#users}.
     *
     * <p>And serialised without each line's {@code stock}, which points straight back here. The
     * hashCode cycle used to hide this one: loading the set re-entered itself and, with assertions
     * off, left it reading empty, so Jackson wrote {@code []} and never followed the loop. Once the
     * set loaded its real lines, every endpoint returning an asset with a stock - the store pages
     * among them - recursed until the stack overflowed. The lines stay in the JSON; the Stock report
     * reads them.
     */
    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true)
    @NotAudited
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties("stock")
    private Set<StockCommodity> stockCommodities;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id", nullable = false)
    @JsonIgnore
    @NotAudited
    private Branch branch;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    @NotAudited
    private Supplier supplier;

    @OneToMany(mappedBy = "stock", cascade = CascadeType.PERSIST)
    @NotAudited
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private List<GrnReport> grnReports;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createDate;

    @LastModifiedDate
    private LocalDateTime lastModified;

    @CreatedBy
    @Column(nullable = false, updatable = false)
    private Long createdBy;

    @LastModifiedBy
    private Long lastModifiedBy;

    @Transient
    private List<CommodityWithDetailsDao> commodities;
}
