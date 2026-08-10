package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.assetType.RepairDestination;

import java.util.List;

/**
 * Sends a faulty asset for repair (§14). Routing follows the asset category's configured
 * {@link RepairDestination} unless overridden here; a non-repairable category is diverted straight to
 * disposal. Optionally issues a temporary replacement to the affected user in the same operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairTransferDTO implements ApprovalBypassRequest {
    private Long assetId;
    private Long repairId;

    /** Overrides the category's default routing (IT / ADMIN / EXTERNAL); null uses the category default. */
    private RepairDestination repairDestination;
    /** Required when the resolved routing is EXTERNAL — the vendor the asset is sent to. */
    private Long consultantId;

    /** When set (with {@code affectedUserId}), also issues this temp asset from the IT store to the user. */
    private Long tempAssetId;
    private Long affectedUserId;

    /**
     * The signed vendor hand-over paperwork for an EXTERNAL repair — required before the movement
     * may be created, which is why it is captured here and not at dispatch like a courier's note.
     */
    private List<String> deliveryDocuments;
    private String remarks;

    /** Set once the initiator has confirmed they want to proceed with no approver available. */
    private Boolean proceedWithoutApproval;

    /** Why the movement is proceeding unapproved. Recorded on the approval trail. */
    private String bypassReason;
}
