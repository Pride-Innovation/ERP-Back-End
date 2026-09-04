/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.request;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class RequestSearchCriteria {
    private String name;
    private String priority;
    private String description;
    private LocalDateTime timeOfSubmissionOfRequest;
    private List<Long> statusIds;
    private List<Long> requesterIds;
    private String requestedBy;
    private String approver;
    private Boolean deleted;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long currentUserId;
    private Long currentApproverId;
    /** The logged-in user's unit id — so the approval inbox also shows requests routed to their unit. */
    private Long currentUnitId;

    /**
     * Excludes statuses a request has finished at.
     *
     * <p>"Open" is defined by exclusion rather than by listing the open statuses, because the open
     * set grows every time an approval step is added to a workflow while the terminal set does not:
     * a request is finished when it was rejected or when the requester acknowledged receipt, and
     * everything in between — every approval tier, issuance, and the movement carrying the item —
     * is still open. Listing the open ones would need editing each time the ladder changed.
     */
    private List<Long> excludedStatusIds;

    /** Restricts to requests raised by this person. Distinct from {@code requesterIds}, which the
     *  role-based subordinate rules populate. */
    private Long raisedByUserId;

    /** Restricts to requests raised by anyone at this branch. */
    private Long requesterBranchId;
}
