package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returns a repaired asset to its originating location (§16). Optionally reassigns it to a user, and
 * optionally returns the temporary replacement asset to the IT store in a second movement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnAfterRepairDTO implements ApprovalBypassRequest {
    private Long assetId;
    /**
     * Optional. Left null, the destination is derived from the holder recorded when the asset was
     * sent for repair — which is the whole point, so that a repaired asset cannot be returned to the
     * wrong branch by a mis-click.
     */
    private Long destLocationId;
    /** Optional; derived from the recorded holder when omitted. */
    private Long recipientUserId;
    /** Required when returning the asset to someone other than its recorded holder. */
    private String overrideReason;
    /**
     * Optional. When set, a second movement returns this temporary asset to its pool store.
     *
     * <p>Normally left null: the loaner is derived from what is actually out with the recipient. It
     * was a manual picker on the form, which made returning a loaner depend on the user remembering
     * to select it — anything forgotten never came back and the pool drained.
     */
    private Long tempAssetId;

    /**
     * Set false when the recipient is keeping the loaner for now, so no return leg is created for
     * it. Null or true derives and returns it.
     */
    private Boolean returnLoaner;
    private String remarks;

    /**
     * What was done to fix it, and the paperwork for it.
     *
     * <p>These close the {@code Repair} that {@code transferForRepair} opened. Returning a repaired
     * asset <em>is</em> the moment the repair finished, so asking for the completion separately —
     * as the old assets-page flow did, through a second endpoint nobody reached — left every
     * maintenance record permanently "Pending".
     *
     * <p>Both optional: a repair with no notes is still a repair that happened, and refusing the
     * return for want of a sentence would strand the asset at Head Office.
     */
    private String completionNotes;
    private java.util.List<String> completionDocuments;

    /** Set once the initiator has confirmed they want to proceed with no approver available. */
    private Boolean proceedWithoutApproval;

    /** Why the movement is proceeding unapproved. Recorded on the approval trail. */
    private String bypassReason;
}
