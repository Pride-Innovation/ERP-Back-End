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
    private int expectedCost;
    private int actualCost;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "commodity_id", nullable = false)
    private Commodity commodity;

}
