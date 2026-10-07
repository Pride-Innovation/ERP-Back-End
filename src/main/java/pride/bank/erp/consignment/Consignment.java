package pride.bank.erp.consignment;

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
import pride.bank.erp.courier.Courier;
import pride.bank.erp.movement.Movement;
import pride.bank.erp.store.Store;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One physical journey, carrying any number of {@link Movement}s to the same destination.
 *
 * <p>Exists because a movement was doing two jobs whose cardinality does not match. A movement is an
 * <i>obligation</i> — these items, owed to this person, under this approval. A consignment is a
 * <i>journey</i> — this courier, this tracking number, this dispatch. One van can carry stock for
 * five different requests to the same branch, and previously that meant five movements each
 * pretending to have their own courier, or one movement that could not say which request each item
 * belonged to.
 *
 * <p>Splitting them also removes the second leg. Fulfilment used to be modelled as two movements —
 * Head Office to the branch store, then branch store to the requester — because a single movement
 * could not represent both the custody stop and the final hand-over. Here the movement runs
 * Head Office → requester throughout, and the branch stop is an event on the consignment: arrival
 * credits the destination store, and each movement completes when its own recipient signs. Two
 * ledger effects, one movement per request.
 *
 * <p>Movements may leave from <b>different source stores</b> (IT and Admin loading the same van) and
 * a consignment may run in either direction — a branch dispatching to Head Office is the same shape
 * as Head Office dispatching to a branch.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Audited
@Table(name = "consignment")
public class Consignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human reference for the paperwork, e.g. {@code CNS/2026/000042}. Assigned server-side. */
    @Column(unique = true)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'DRAFT'")
    @Builder.Default
    private ConsignmentStatus status = ConsignmentStatus.DRAFT;

    // ── Route ────────────────────────────────────────────────────────────────

    /**
     * Where the journey starts. Held on the consignment rather than derived from its movements
     * because those may leave from several stores in the same location.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "source_location_id")
    @NotAudited
    private Branch sourceLocation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dest_location_id")
    @NotAudited
    private Branch destLocation;

    /**
     * The store the goods land in on arrival — the destination location's Admin store. Resolved at
     * arrival and kept so each movement knows which balance to draw down when it is handed over.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "landing_store_id")
    @NotAudited
    private Store landingStore;

    // ── Logistics ────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "courier_id")
    @NotAudited
    private Courier courier;

    /** Courier name as recorded, so the paperwork survives a courier being renamed or retired. */
    private String courierService;
    /** Issued by the system - see {@code TrackingNumberService}. Never accepted from the client. */
    private String trackingNumber;
    /** The courier's own waybill or reference, typed at dispatch. Theirs, so not held unique. */
    private String courierReference;
    private String plateNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    private LocalDateTime arrivalDate;

    @ElementCollection
    @CollectionTable(name = "consignment_delivery_documents",
            joinColumns = @JoinColumn(name = "consignment_id"))
    @Column(name = "document")
    @NotAudited
    @Builder.Default
    private List<String> deliveryDocuments = new ArrayList<>();

    private String remarks;

    // ── People ───────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "initiator_id")
    @NotAudited
    private User initiator;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiving_officer_id")
    @NotAudited
    private User receivingOfficer;

    // ── Contents ─────────────────────────────────────────────────────────────

    /**
     * LAZY, unlike most associations here: a consignment can hold many movements and each movement
     * drags in nine eager associations of its own. Loading them by default would put the same
     * 61-table join limit that broke movement detail back in play.
     */
    @OneToMany(mappedBy = "consignment", fetch = FetchType.LAZY)
    @NotAudited
    @Builder.Default
    private List<Movement> movements = new ArrayList<>();

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
