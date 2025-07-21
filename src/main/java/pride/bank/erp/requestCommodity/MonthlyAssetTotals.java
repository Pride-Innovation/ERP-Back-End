package pride.bank.erp.requestCommodity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyAssetTotals {
    private String month;
    private int itEquipment;
    private int officeEquipment;
}
