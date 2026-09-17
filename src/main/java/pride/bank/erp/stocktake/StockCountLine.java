/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.commodity.Commodity;

import java.time.LocalDateTime;

/** One commodity on a count sheet. */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(
        name = "stock_count_line",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_count_line_commodity",
                columnNames = {"stock_count_id", "commodity_id"}
        )
)
public class StockCountLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_count_id", nullable = false)
    @JsonIgnore
    private StockCount stockCount;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "commodity_id", nullable = false)
    private Commodity commodity;

    /**
     * What the system held when the count was opened. Frozen here so the variance is measured
     * against a known point in time rather than against a number that may have moved since.
     */
    @Column(nullable = false)
    @Builder.Default
    private int systemQuantityAtStart = 0;

    /** What was physically on the shelf. Null until the line has actually been counted. */
    private Integer countedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(30)")
    private AdjustmentReason reason;

    @Column(length = 500)
    private String note;

    /**
     * Set when the balance moved between the snapshot and posting. Neither
     * {@code counted - snapshot} (ignores the movement) nor {@code counted - current}
     * (double-counts it) is right, so the line is sent back to be counted again rather than
     * guessed at.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean recountRequired = false;

    private LocalDateTime countedAt;

    /** Convenience for the UI; {@code null} while the line is uncounted. */
    @Transient
    public Integer getVariance() {
        return countedQuantity == null ? null : countedQuantity - systemQuantityAtStart;
    }
}
