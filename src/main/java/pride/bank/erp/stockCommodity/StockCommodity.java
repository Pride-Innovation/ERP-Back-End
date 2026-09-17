/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stockCommodity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.stock.Stock;

import java.math.BigDecimal;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCommodity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int orderedQuantity;
    private int deliveredQuantity;
    /**
     * Unit prices, in UGX. {@link BigDecimal} rather than {@code int}: an int capped a single value
     * near 2.1bn and could not hold a fraction at all, which made depreciation arithmetic — a rate
     * applied to a cost, then divided by twelve — impossible to express without losing money to
     * rounding on every line.
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal costPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal purchasePrice;
    private int registeredNumber;

    /**
     * Optimistic lock. Receiving is a read-modify-write on {@code deliveredQuantity} and
     * {@code registeredNumber}; without this, two storekeepers receiving the same order at the same
     * moment silently lose one of the batches (last write wins) while both GRNs still print.
     */
    @Version
    @Column(nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long version;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "commodity_id", nullable = false)
    private Commodity commodity;

}
