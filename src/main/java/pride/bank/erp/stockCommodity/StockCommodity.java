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
    private int costPrice;
    private int purchasePrice;
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
