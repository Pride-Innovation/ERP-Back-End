package pride.bank.erp.repair;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.asset.AssetDAO;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepairDTO {
    private Long id;
    private LocalDateTime repairStartDate;
    private LocalDateTime repairEndDate;
    private String technician;
    /** Null on a record with nobody recorded, or one typed as free text before V26. */
    private TechnicianType technicianType;
    private Long technicianUserId;
    private Long technicianConsultantId;
    private pride.bank.erp.assetType.RepairDestination repairDestination;
    private String repairReason;
    private String status;
    private List<String> documents;
    private AssetDAO asset;  // A smaller Asset DTO
    private String completionNotes;
    private List<String> completionDocuments;
}
