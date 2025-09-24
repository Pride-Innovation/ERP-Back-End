package pride.bank.erp.stock;

import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSearchCriteria {
    private String lpoNumber;
    private Long stockStatusId;
    private Boolean disposed;
}
