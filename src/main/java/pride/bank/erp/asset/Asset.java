/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

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
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.status.Status;
import pride.bank.erp.stock.Stock;
import pride.bank.erp.store.Store;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Audited
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String assetName;
    private String hostname;
    private String detailNetBookValue;
    private String engravedNumber;
    private String dateReceipt;
    private String make;
    private String unitOfMeasure;
    /**
     * An LPO number recorded as text, for an asset with no stock order (one brought in by the bulk
     * import). Optional and a reference only: a received asset's LPO is its stock order's, and nothing
     * looks an order up by this. See V33.
     */
    @Column(name = "lpo_reference", length = 100)
    private String lpoReference;
    /**
     * Purchase cost and book cost in UGX. Previously stored as free-text {@code String}, so no
     * arithmetic was possible without parsing, and nothing prevented a non-numeric value being
     * saved — untenable for depreciation, which has to multiply and divide these.
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal purchaseCost;

    @Column(precision = 19, scale = 4)
    private BigDecimal costOfTheAsset;
    private String netValueB;
    private String model;
    private String serialNumber;
    private String ram;
    private String cpuSpeed;
    private String hardDiskSize;
    private String macAddress;
    private String ipAddress;
    private String interfaceType;
    private String assetDepreciationRate;
    private String description;
    private String image;
    @Builder.Default
    private Boolean disposed = false;
    private LocalDateTime disposalDate;

    /**
     * Soft delete: the record is hidden from every listing but kept in the database.
     *
     * <p>Deliberately separate from {@link #disposed}, which is a business event — an asset reaching
     * the end of its life, written off through a movement, still part of the register and counted as
     * disposed. This says something narrower and more administrative: the record should not have
     * existed. A duplicate, a mis-keyed import row. An asset can legitimately be both.
     *
     * <p>Kept rather than deleted so its assignment history and repair history still point at
     * something real, and so the audit trail can be read back. That is the whole reason for the
     * flag; a hard delete would leave both trails referring to a row that is gone.
     */
    @Builder.Default
    private Boolean deleted = false;
    private String lastRepairedBy;

    /**
     * True when this asset is pool stock kept aside for temporary replacement loans while another
     * asset of the same category is under repair (see {@code RepairMovementService}), rather than an
     * operational asset assigned to a specific person/desk.
     */
    @Column(name = "temporary_pool", columnDefinition = "BIT NOT NULL DEFAULT 0")
    @Builder.Default
    private boolean temporaryPool = false;

    @Column(unique = true)
    private String tagName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    @NotAudited
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assignedTo_id")
    @NotAudited
    private User assignedTo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    @NotAudited
    private Branch branch;

    /**
     * The store currently holding this asset when it is not assigned to a user (e.g. an Admin store,
     * the IT store while under repair, or the Disposal store). Null while assigned to a user.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_store_id")
    @NotAudited
    private Store currentStore;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_id")
    @NotAudited
    private Status assetStatus;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assetType_id")
    @NotAudited
    private AssetType assetType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "commodity_id")
    @NotAudited
    private Commodity commodity;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_id")
    @NotAudited
    private Stock stock;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createDate;

    @LastModifiedDate
    private LocalDateTime lastModified;

    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @LastModifiedBy
    private Long lastModifiedBy;
}
