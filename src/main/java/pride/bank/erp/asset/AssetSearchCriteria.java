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
     * The department of whoever holds the asset.
     *
     * <p><b>An asset has no department of its own</b> — only the person holding it does. So this
     * answers "assets currently held by somebody in that department", which is a narrower question
     * than the bare label implies and excludes every unassigned item by construction.
     *
     * <p>Measured when it was added: of 252 assets, <b>105 are unassigned</b> and only <b>18 are held
     * by somebody with a department recorded</b> — every one of them at Head Office, because no
     * branch staff have departments. So this filter can currently match at most 7% of the estate, and
     * for a branch-scoped viewer it can only ever match nothing. The page says so rather than
     * rendering an empty table that reads as "there are none of those".
     */
    private Long departmentId;

    /**
     * A branch the caller chose, matched by id on the asset's branch or its holder's — the same pair
     * {@link #scopedBranchId} uses. A choice, not a limit: it narrows inside the scope and cannot
     * widen it, because the scope predicate is applied as well.
     */
    private Long branchId;

    /** Bounds on {@code Asset.disposalDate}, for the disposal report. */
    private LocalDateTime disposedFrom;
    private LocalDateTime disposedTo;

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
     * Also admit, in a branch-scoped listing, this branch's assets that are <em>away</em> - in
     * maintenance or pending disposal, sent by this branch on their newest repair or disposal
     * transfer. Receipt rewrites {@code asset.branch} to Head Office and the transfer clears the
     * holder, so neither scope column still names the branch that owns them.
     *
     * <p><b>Server-resolved</b> and set only when asked for: the reports page asks, the assets page
     * does not - its row menu offers Edit and Reassign, which an away asset at Head Office refuses.
     */
    private Long awayFromBranchId;

    /**
     * The Disposal report: scope and Branch filter by <em>whose write-off it was</em> (the branch on
     * the newest disposal transfer) instead of where the asset stands. Honoured only with
     * {@code disposed = true}.
     */
    private Boolean byDisposingBranch;

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
