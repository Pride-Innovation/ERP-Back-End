/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.store.Store;

import java.time.LocalDateTime;

/**
 * An immutable record of a balance being corrected to match reality, and why.
 *
 * <p>This is the first slice of the stock ledger, and deliberately the slice where it matters most.
 * Receipts and issues already have documents behind them — a GRN, an issuance record — so you can
 * always ask why they happened. An adjustment had nothing: the balance simply became a different
 * number. Every posting now leaves a row here.
 *
 * <p>Never updated or deleted. A wrong adjustment is corrected by counting again and posting
 * another one, so the history stays a truthful account of what was believed and when.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "stock_adjustment")
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "commodity_id", nullable = false)
    private Commodity commodity;

    /** Signed: positive wrote stock up, negative wrote it down. Never zero. */
    @Column(nullable = false)
    private int quantityDelta;

    /** The balance before and after, so a row explains itself without replaying history. */
    @Column(nullable = false)
    private int quantityBefore;

    @Column(nullable = false)
    private int quantityAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30) NOT NULL")
    private AdjustmentReason reason;

    @Column(length = 500)
    private String note;

    /** The count that produced this adjustment. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_count_id")
    private StockCount stockCount;

    /** Who approved the variance — the accountable party, not whoever clicked post. */
    private Long approvedBy;

    @Column(nullable = false)
    private LocalDateTime postedAt;

    private Long postedBy;
}
