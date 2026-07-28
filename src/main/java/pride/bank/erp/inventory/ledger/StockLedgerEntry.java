/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.inventory.ledger;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.store.Store;

import java.time.LocalDateTime;

/**
 * One immutable record of stock entering or leaving a store.
 *
 * <p>Until now {@code StoreBalance.quantity} was a bare running total. You could see that a store
 * held twelve of something, but not how it got to twelve, and if the number was wrong there was no
 * way to work out where it went wrong or to rebuild it. Envers recorded <i>who</i> changed a balance
 * and <i>when</i>, but never <i>why</i> or <i>against which document</i>.
 *
 * <p>Every credit and debit now writes a row here, carrying the reason and a pointer back to the
 * document that caused it — a stock receipt, an issuance, a movement, a stock count. The balance
 * becomes a cache of these rows rather than the only copy of the truth, and
 * {@code StockLedgerService.verify} can recompute it and report any drift.
 *
 * <p>Append-only. A wrong entry is corrected by posting an opposing one, never by editing history.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(
        name = "stock_ledger_entry",
        indexes = {
                @Index(name = "idx_ledger_store_commodity", columnList = "store_id,commodity_id"),
                @Index(name = "idx_ledger_occurred", columnList = "occurred_at"),
                @Index(name = "idx_ledger_source", columnList = "source_type,source_id")
        }
)
public class StockLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "commodity_id", nullable = false)
    private Commodity commodity;

    /** Signed: positive brought stock in, negative took it out. Never zero. */
    @Column(nullable = false)
    private int quantityDelta;

    /**
     * The balance after this entry was applied.
     *
     * <p>Redundant against replaying the whole history, and deliberately so: it makes a single row
     * self-explanatory, and lets drift be spotted by comparing consecutive entries rather than
     * recomputing everything.
     */
    @Column(nullable = false)
    private int balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30) NOT NULL")
    private StockLedgerReason reason;

    /** What caused it, e.g. {@code STOCK_RECEIPT}, {@code MOVEMENT}, {@code STOCK_COUNT}. */
    @Column(length = 40)
    private String sourceType;

    /** The id of that document, so a row can be traced back to it. */
    private Long sourceId;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    /** Who caused it. Null for system-initiated changes such as seeding. */
    private Long actorId;
}
