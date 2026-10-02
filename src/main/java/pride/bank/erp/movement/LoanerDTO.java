/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A temporary replacement still out with somebody, and enough to decide what to do about it.
 *
 * <p>{@code coversAssetLabel} is what tells a holder of several loaners which is which. It is null
 * for a loaner issued without naming the repair it covers - legitimately, since a loaner can stand
 * in for an asset going to disposal, or for nothing in particular.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanerDTO {
    private Long assetId;
    private String engravedNumber;
    private String assetName;
    private String category;

    private Long holderId;
    private String holderName;
    private String holderBranchName;

    /** When the loan began - when the holder signed for the loaner. */
    private LocalDateTime since;

    /** The asset this loaner stands in for, when the issue named one. */
    private Long coversAssetId;
    private String coversAssetLabel;
    /** "Pending" while that repair runs, "Completed" once it has closed. */
    private String coversRepairStatus;
    /** True once the covered asset is back in the holder's hands - the loaner is then due. */
    private boolean coveredAssetBack;

    /** Where a return sends it: the store it was lent from. */
    private String returnStoreName;

    /** Set when a return is already under way, so it is not offered twice. */
    private Long pendingReturnMovementId;
}
