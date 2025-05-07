package pride.bank.erp.stockCommodity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockCommodityDTO {
    private int orderedQuantity;
    private int deliveredQuantity;
    private int expectedCost;
    private int actualCost;
    private Long commodityId;
}
