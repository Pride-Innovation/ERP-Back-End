package pride.bank.erp.commodity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Sort;

@Getter
@Setter
@Builder
public class CommodityPage {
    private Integer pageNumber;
    private Integer pageSize;
    private Sort.Direction sortDirection;
    private String sortBy;
}
