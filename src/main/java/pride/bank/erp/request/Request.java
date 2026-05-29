/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.request;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestReport.RequestReport;
import pride.bank.erp.requestReport.RequestReportDAO;
import pride.bank.erp.status.Status;
import pride.bank.erp.unit.Unit;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Audited
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String priority;
    private String description;
    private String signaturePath;
    @Builder.Default
    private Boolean deleted = false;
    private LocalDateTime deletionDate;
    private Boolean isBranchStationery; // Tracks branch stationery requests.
    private Boolean isBranchNonStationery; // Tracks branch non stationery requests.

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_id")
    @NotAudited
    private Status status;

    private LocalDateTime timeOfSubmissionOfRequest;

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

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @NotAudited
    private User requester;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @NotAudited
    private Set<RequestCommodity> requestCommodities;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @NotAudited
    private Set<RequestReport> requestReports;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_approver_id")
    private User currentApprover;

    /**
     * The unit currently responsible for the request, when the active step targets a unit's
     * group mailbox. Lets unit members see and act on the request in their pending inbox even
     * though there is no single individual approver.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_unit_id")
    @NotAudited
    private Unit currentUnit;

    /** Primary asset category for the request; drives workflow resolution and custom attribute validation. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_type_id")
    @NotAudited
    private AssetType assetType;

    /**
     * Free-form map of attribute values keyed by {@code AssetTypeCustomAttribute.key}
     * on the chosen {@link #assetType}. Validated on save when {@code assetType} is set.
     * Stored as a JSON object in a large-text column.
     */
    // MSSQL (uncomment + comment-out the MySQL line below when switching back):
    // @Column(name = "attributes_json", columnDefinition = "NVARCHAR(MAX)")
    // MySQL:
    @Column(name = "attributes_json", columnDefinition = "LONGTEXT")
    @Convert(converter = RequestAttributesConverter.class)
    private Map<String, Object> attributes;

    @Transient
    private List<CommodityWithQuantityDTO> commodities;

    @Transient
    private String emailMessage;

    @Transient
    private List<RequestReportDAO> requestReportDAOS;
}
