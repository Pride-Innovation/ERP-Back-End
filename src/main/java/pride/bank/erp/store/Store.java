package pride.bank.erp.store;

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
import pride.bank.erp.department.Department;

import java.time.LocalDateTime;

/**
 * A physical store at a {@link #location} (CLAUDE.md §4). The on-hand quantities held by a store are
 * recorded as {@link StoreBalance} rows, and stores are the source/destination of {@code Movement}s.
 *
 * <p>Identity is {@code (location, storeType, department, courier)}:
 * <ul>
 *   <li>Every location has exactly one location-level {@link StoreType#ADMIN} store
 *       ({@code department == null}).</li>
 *   <li>Head Office additionally has one {@link StoreType#IT} and one {@link StoreType#DISPOSAL}
 *       store.</li>
 *   <li>A non-null {@link #department} models department-level holdings (§12).</li>
 *   <li>A {@link StoreType#COURIER} store's {@link #location} is fixed to Head Office purely to
 *       satisfy this column's {@code NOT NULL} constraint — its real identity is {@link #courier}
 *       (one custody store per courier), not its location.</li>
 * </ul>
 *
 * <p>Mapped to the {@code store_container} table: the legacy {@code store} table now backs
 * {@link StoreBalance} (see that class for the rationale under {@code ddl-auto=update}).
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Audited
@Table(
        name = "store_container",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_store_location_type_department_courier",
                columnNames = {"location_id", "store_type", "department_id", "courier_id"}
        )
)
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    /** Human-readable label, e.g. "Gulu Branch Admin Store". */
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "store_type", nullable = false, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'ADMIN'")
    @Builder.Default
    private StoreType storeType = StoreType.ADMIN;

    /**
     * Where the store physically is. <b>Null for a {@link StoreType#COURIER} custody store</b>, which
     * has no location — its identity is its {@link #courier}, and whatever it holds is between two
     * places rather than at one.
     *
     * <p>This used to be {@code NOT NULL}, so courier stores were pinned to Head Office to satisfy
     * the column. The consequence was that every unit and every asset in transit anywhere in the
     * country reported as Head Office stock, and the balance queries had to be littered with
     * defensive filters to work around it.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "location_id")
    @NotAudited
    private Branch location;

    /** Non-null only for department-level stores (§12); null for a location-level store. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    @NotAudited
    private Department department;

    /** Non-null only for a {@link StoreType#COURIER} store — the courier this custody store belongs to. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "courier_id")
    @NotAudited
    private Courier courier;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

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
