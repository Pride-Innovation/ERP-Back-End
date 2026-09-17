/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetAssignmentHostory;

import lombok.*;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AssetAssignmentHistorySearchCriteria {
    private Long assetId;

    /**
     * Narrows the listing to one branch. Applied from the caller's permissions, never from the
     * request — this endpoint answers with no asset id at all, and without a scope that is the whole
     * estate's chain of custody in one page.
     */
    private Long scopedBranchId;

    /** Narrows further to what the caller personally holds. Non-null only at SELF scope. */
    private Long scopedOwnerId;
}
