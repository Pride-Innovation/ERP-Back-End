package pride.bank.erp.stock;

import lombok.*;

import java.time.LocalDateTime;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSearchCriteria {
    private String lpoNumber;
    private Long stockStatusId;
    private Boolean disposed;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
