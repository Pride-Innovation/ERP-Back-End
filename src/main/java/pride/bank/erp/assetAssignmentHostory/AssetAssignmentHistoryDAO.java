package pride.bank.erp.assetAssignmentHostory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.status.Status;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AssetAssignmentHistoryDAO {
    private Long id;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private User user;
    private Asset asset;
    private Status statusBefore;
    private Status statusAfter;
}
