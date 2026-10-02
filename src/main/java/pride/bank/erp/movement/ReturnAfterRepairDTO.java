package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returns a repaired asset to its originating location (§16), back to the holder who sent it. Any
 * loaner the holder has is left with them until they hand it back themselves.
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
    /*
     * There is no loaner field here any more. Returning a repaired asset used to take a loaner back
     * in the same call; a loaner is now returned by hand once the user has their own asset - see
     * LoanerService.
     */
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

    /**
     * Who repaired it. Required unless the repair already names somebody - a closed repair must say
     * who did the work. Same shape as the transfer and the bench completion, so one picker serves
     * all three.
     */
    private pride.bank.erp.repair.TechnicianType technicianType;
    private Long technicianUserId;
    private Long technicianConsultantId;

    /** Set once the initiator has confirmed they want to proceed with no approver available. */
    private Boolean proceedWithoutApproval;

    /** Why the movement is proceeding unapproved. Recorded on the approval trail. */
    private String bypassReason;
}
