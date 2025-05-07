package pride.bank.erp.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.commodity.Commodity;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommodityWithDetailsDao {
    private Commodity commodity;
    private int orderedQuantity;
    private int deliveredQuantity;
    private int expectedCost;
    private int actualCost;
}
