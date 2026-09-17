package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Issues a temporary replacement asset from the IT store to a user while a repair runs (§15). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TempReplacementDTO implements ApprovalBypassRequest {
    private Long tempAssetId;
    private Long recipientUserId;
    private String remarks;

    /** Set once the initiator has confirmed they want to proceed with no approver available. */
    private Boolean proceedWithoutApproval;

    /** Why the movement is proceeding unapproved. Recorded on the approval trail. */
    private String bypassReason;
}
