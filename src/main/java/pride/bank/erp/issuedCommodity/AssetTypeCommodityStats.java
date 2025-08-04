package pride.bank.erp.issuedCommodity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AssetTypeCommodityStats {
    private String assetType;
    private String mostStockedItem;
    private long mostStockedQuantity;
    private String leastStockedItem;
    private long leastStockedQuantity;
}
