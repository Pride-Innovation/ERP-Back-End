/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for handing an asset back into a store. Both fields are optional. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendToStoreDTO {

    /** Destination store; defaults to the ADMIN store of the branch currently holding the asset. */
    private Long storeId;

    /**
     * Whether the asset becomes loanable pool stock for temporary replacements. Null leaves the
     * current setting alone; the flag only means anything once the asset is actually in a store,
     * which is why it is set as part of this move rather than on its own.
     */
    private Boolean temporaryPool;

    /**
     * Set on a second attempt, once the person has confirmed they want to proceed with no approver
     * available. A hand-back from a holder at one branch into a store at another is a cross-location
     * movement and climbs the approval ladder like any other; where nobody can approve it, this is the
     * same recorded override every movement form offers.
     */
    private Boolean proceedWithoutApproval;
    private String bypassReason;
}
