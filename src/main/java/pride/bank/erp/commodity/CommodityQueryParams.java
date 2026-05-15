package pride.bank.erp.commodity;

import lombok.Getter;
import lombok.Setter;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;

@Getter
@Setter
public class CommodityQueryParams {
    private int pageSize = 10;
    private int pageNumber = 0;
    private String sortBy = "id";
    private String sortDirection = ASCENDING_SORT_DIRECTION;
    private String name;
    private String groupName;
    private Long assetTypeId;
}
