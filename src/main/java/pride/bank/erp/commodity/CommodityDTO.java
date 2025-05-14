package pride.bank.erp.commodity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommodityDTO {
    private Long assetType;
    private String groupName;
    private String name;
}
