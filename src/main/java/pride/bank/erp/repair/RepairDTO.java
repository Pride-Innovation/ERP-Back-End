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
    /**
     * The branch this repair belongs to - the one its opening transfer sent the asset from - for the
     * maintenance report. Not {@code asset.branch}: that reads Head Office while the asset is on the
     * bench. Set only by the report listing.
     */
    private Long sentFromBranchId;
    private String sentFromBranchName;
    private String repairReason;
    private String status;
    private List<String> documents;
    private AssetDAO asset;  // A smaller Asset DTO
    private String completionNotes;
    private List<String> completionDocuments;
}
