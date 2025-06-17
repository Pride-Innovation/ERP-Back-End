package pride.bank.erp.asset;

import lombok.*;
import org.springframework.data.domain.Sort;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AssetPage {
    private Integer pageNumber;
    private Integer pageSize;
    private Sort.Direction sortDirection;
    private String sortBy;
}
