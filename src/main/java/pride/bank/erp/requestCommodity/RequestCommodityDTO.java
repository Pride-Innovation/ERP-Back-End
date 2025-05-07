package pride.bank.erp.requestCommodity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestCommodityDTO {
    private int quantity;
    private Long commodityId;
}
