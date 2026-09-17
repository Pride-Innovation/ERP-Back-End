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

    /**
     * An existing maintenance record to attach to, instead of opening a new one.
     *
     * <p>Normally null. Left null, {@code transferForRepair} opens a {@code Repair} for this asset
     * and links it — which is what makes the maintenance report and the asset's Repair History tab
     * reflect a repair that was actually done. Measured before this was wired: <b>24 repair
     * movements, 0 repair records</b>, because the two flows wrote to two different places and only
     * the unused one wrote a maintenance record.
     */
    private Long repairId;

    /**
     * What is wrong with the asset. Required when opening a new maintenance record.
     *
     * <p>Deliberately separate from {@link #remarks}: remarks describe the <em>journey</em> (who to
     * call at the far end, what else is in the box) and live on the movement, while this describes
     * the <em>fault</em> and lives on the repair. Folding them together would mean the maintenance
     * report's only column about the failure was filled with courier notes.
     */
    private String faultDescription;

    /**
     * Who is expected to work on it, if known at dispatch.
     *
     * <p>Optional, because for an IT- or Admin-routed repair the bench decides that after it arrives.
     * For an EXTERNAL repair it defaults to the consultant's name, which is the answer by
     * construction — nobody should have to type what the form already knows.
     */
    private String technician;

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
