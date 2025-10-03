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
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.user.User;

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
    private String purchaseCost;
    private String costOfTheAsset;
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
    private Boolean disposed = false;
    private LocalDateTime disposalDate;
    private String lastRepairedBy;

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
