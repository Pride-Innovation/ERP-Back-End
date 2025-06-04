package pride.bank.erp.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Summation {
    private int orderedCommoditiesSummation;
    private int deliveredCommoditiesSummation;
}
