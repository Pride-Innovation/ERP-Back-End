package pride.bank.erp.requestCommodity;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class MonthlyStationeryStockDTO {
    private String month;
    private Map<String, Integer> commodities; // e.g. "Pens": 40, "Books": 20
}