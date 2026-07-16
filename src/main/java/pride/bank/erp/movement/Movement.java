package pride.bank.erp.movement;

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
import pride.bank.erp.consultant.Consultant;
import pride.bank.erp.courier.Courier;
import pride.bank.erp.repair.Repair;
import pride.bank.erp.request.Request;
import pride.bank.erp.store.Store;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A single transfer of inventory or assets between stores / to a user (CLAUDE.md §18).
 *
 * <p>Location and department are read from {@link #sourceStore} and {@link #destStore} (Branch is the
 * location), so they are not duplicated here. {@link #movementCategory} is derived from those at
 * creation. {@link #request} / {@link #repair} are nullable: request-driven fulfilment links the
 * request; repair / disposal / replenishment movements may link a repair or stand alone.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Audited
@Table(name = "movement")
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Classification ──────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(40) NOT NULL")
    private MovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) NOT NULL")
    private MovementCategory movementCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'INITIATED'")
    @Builder.Default
    private MovementStatus status = MovementStatus.INITIATED;

    // ── Source ──────────────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "source_store_id", nullable = false)
    @NotAudited
    private Store sourceStore;

    // ── Destination (a store, or a user for store→user issuance) ─────────────
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dest_store_id")
    @NotAudited
    private Store destStore;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recipient_user_id")
    @NotAudited
    private User recipientUser;

    // ── Logistics (populated only for INTER_LOCATION movements) ──────────────
    private String courierService;
    private String trackingNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;

    /** The courier holding custody of these items between dispatch and receipt. Set at dispatch. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "courier_id")
    @NotAudited
    private Courier courier;

    /** Vehicle plate number captured at dispatch — vetted and ad-hoc couriers alike. */
    private String plateNumber;

    @ElementCollection
    @CollectionTable(name = "movement_delivery_documents", joinColumns = @JoinColumn(name = "movement_id"))
    @Column(name = "document")
    @NotAudited
    @Builder.Default
    private List<String> deliveryDocuments = new ArrayList<>();

    // ── Receiving ───────────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiving_officer_id")
    @NotAudited
    private User receivingOfficer;

    private LocalDateTime receiptDate;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(30) DEFAULT 'PENDING'")
    @Builder.Default
    private ReceiptStatus receiptStatus = ReceiptStatus.PENDING;

    private String remarks;

    // ── Audit / links ───────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "initiator_id")
    @NotAudited
    private User initiator;

    /**
     * Approval routing (standalone movements only). While the movement is in {@code DRAFT} it climbs
     * the initiator's reporting ladder: {@code currentApprover} is the tier that must act now, and
     * {@code approvalTierIndex} is that tier's position in the resolved chain. Both are null/0 for
     * request-fulfilment movements, which skip approval.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_approver_id")
    @NotAudited
    private User currentApprover;

    @Builder.Default
    private int approvalTierIndex = 0;

    private LocalDateTime completionDate;

    /**
     * True once this movement's final (courier → destination, or source → destination when no
     * courier is involved) inventory effects have been applied. Guards the ledger so completion is
     * never double-counted.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean inventorySettled = false;

    /**
     * True once the first ledger hop — source store → the courier's custody store — has been
     * applied at {@code IN_TRANSIT}. Only ever set for {@link MovementCategory#INTER_LOCATION}
     * movements with a {@link #courier} assigned; guards that hop the same way
     * {@link #inventorySettled} guards the final one.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean custodyTransferSettled = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "request_id")
    @NotAudited
    private Request request;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "repair_id")
    @NotAudited
    private Repair repair;

    /**
     * External repair vendor this asset was sent to, populated only for EXTERNAL repair routing
     * (see {@link pride.bank.erp.assetType.RepairDestination}). Null for in-house movements.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "consultant_id")
    @NotAudited
    private Consultant consultant;

    @OneToMany(mappedBy = "movement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<MovementItem> items = new ArrayList<>();

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
