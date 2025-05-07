package pride.bank.erp.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.commodity.Commodity;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommodityWithQuantityDTO {
    private Commodity commodity;
    private int quantity;
}
