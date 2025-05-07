package pride.bank.erp.role;

import lombok.*;
import org.springframework.data.domain.Sort;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePage {
    private Integer pageNumber;
    private Integer pageSize;
    private Sort.Direction sortDirection;
    private String sortBy;
}
