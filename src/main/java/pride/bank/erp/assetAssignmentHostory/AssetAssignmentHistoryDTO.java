package pride.bank.erp.assetAssignmentHostory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssetAssignmentHistoryDTO {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long userId;
    private Long assetId;
    private Long statusBeforeId;
    private Long statusAfterId;
}
