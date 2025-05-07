package pride.bank.erp.title;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Sort;

@Getter
@Setter
@Builder
public class TitlePage {
    private Integer pageNumber;
    private Integer pageSize;
    private Sort.Direction sortDirection;
    private String sortBy;
}
