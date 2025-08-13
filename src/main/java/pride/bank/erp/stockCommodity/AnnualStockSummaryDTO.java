package pride.bank.erp.stockCommodity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AnnualStockSummaryDTO {
    private String month;
    private Long itEquipment;
    private Long officeEquipment;
}
