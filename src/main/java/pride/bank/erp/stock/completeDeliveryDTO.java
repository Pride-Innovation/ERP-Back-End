package pride.bank.erp.stock;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.stockCommodity.StockCommodityDTO;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class completeDeliveryDTO {
    List<StockCommodityDTO> additionalDeliveries;
    String grnNumber;
}
