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
import pride.bank.erp.commodity.Commodity;

import java.time.LocalDateTime;

/**
 * On-hand quantity of a single {@link Commodity} within a single {@link Store}.
 *
 * <p>This is the former {@code Store} entity, renamed to {@code StoreBalance} to make room for the
 * {@link Store} <i>container</i> (location + type + department). The table name is intentionally kept
 * as {@code store} so existing data and audit history ({@code store_AUD}) are preserved under
 * {@code ddl-auto=update}; only the {@code store_id} container link is added.
 *
 * <p>{@link #branch} is retained (it always equals {@code store.location}) so existing branch-scoped
 * queries keep working; it is the denormalised location of this balance line.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Audited
@Table(
        name = "store",
        // A commodity appears at most once per store. This supersedes the legacy
        // (commodity_id, branch_id) uniqueness, which only worked while every balance lived in a
        // branch's single ADMIN store; the legacy index is dropped at startup by
        // StoreSeeder#relaxLegacyBalanceUniqueness so a branch can hold the same commodity across
        // multiple store types (e.g. HO ADMIN + IT + Disposal).
        uniqueConstraints = @UniqueConstraint(
                name = "uk_store_balance_commodity_store",
                columnNames = {"commodity_id", "store_id"}
        )
)
public class StoreBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private int quantity;

    /**
     * Reorder threshold: the balance is "low stock" when {@code quantity <= minLevel} and
     * {@code minLevel > 0}. 0 (default) = no threshold configured, so it's never flagged.
     */
    @Column(columnDefinition = "INT NOT NULL DEFAULT 0")
    private int minLevel;

    /**
     * Optimistic lock. Every credit (receiving) and debit (issuance) is a read-modify-write on
     * {@link #quantity}, so concurrent writers would otherwise overwrite each other's balance.
     * {@code @NotAudited} — the lock counter is bookkeeping, not history, and keeping it off
     * {@code store_AUD} avoids an audit-table column for it.
     */
    @Version
    @NotAudited
    @Column(nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long version;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "commodity_id", nullable = false)
    @NotAudited
    private Commodity commodity;

    /**
     * Denormalised location of this balance — always equal to {@code store.location}.
     *
     * <p>Null for a courier custody balance, which is between two places rather than at one. Every
     * branch-scoped query already excludes courier stores, so a null here is never surfaced as
     * branch stock.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    @NotAudited
    private Branch branch;

    /**
     * The typed store this balance belongs to. Nullable in the mapping so {@code ddl-auto=update}
     * can add the column to pre-existing rows; backfilled to the branch's ADMIN store by
     * {@code StoreSeeder} and always set for balances created after this change.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    @NotAudited
    private Store store;

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
