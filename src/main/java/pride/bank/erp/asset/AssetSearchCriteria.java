/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import lombok.*;

import java.time.LocalDateTime;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetSearchCriteria {
    private String assetName;
    private Long assetTypeId;
    private Long assetStatusId;
    private Long commodityId;
    private String engravedNumber;
    private Long assignedToId;
    private Boolean disposed;
    private String model;
    private String assignedTo;
    private String location;
    /**
     * Hard branch restriction applied on top of every other filter. Set by the service from the
     * caller's permissions, never from the request — a client cannot widen its own scope.
     */
    private Long scopedBranchId;

    /**
     * Narrows the listing to assets this user personally holds. Non-null only at SELF scope.
     *
     * <p>Separate from {@code assignedToId}, which is the user's own "Assigned to" filter: one is a
     * choice the user made and the other is a limit imposed on them, and collapsing the two would
     * let clearing the filter widen the scope.
     */
    private Long scopedOwnerId;

    /**
     * Shows the soft-deleted register instead of the live one.
     *
     * <p>Not "live plus deleted" — deleted assets are a separate view, reached deliberately by
     * someone holding {@code DELETE_ASSET} who is looking for something to restore. Mixing them into
     * the ordinary listing would put records back in front of everyone that the delete existed to
     * take away.
     */
    private Boolean includeDeleted;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
