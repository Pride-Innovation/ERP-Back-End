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

    /**
     * Filter: show only requests currently with this approver.
     *
     * <h2>Deliberately not {@link #currentApproverId}</h2>
     * They look like the same thing and are not. {@code currentApproverId} means <em>"this is an
     * approval inbox"</em>, and {@code RequestSearchDao} <b>drops the requester scope entirely</b>
     * when it is set — correct there, because an approver often sits above the requester and the
     * subordinate restriction would hide the very work the inbox exists to show.
     *
     * <p>Reusing it for an ordinary filter would therefore turn a dropdown into a way of switching
     * scoping off: pick any approver and see every branch's requests. This one narrows and never
     * widens, so it is a separate field and the scope predicate still applies alongside it.
     */
    private Long approverId;
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

    /**
     * The department of whoever raised the request.
     *
     * <p>A request has no department of its own; it belongs to wherever the requester works. So this
     * answers "requests raised by somebody in that department".
     *
     * <p>Measured when it was added: of 60 requests, <b>12 could match</b> — Business Technology 5,
     * Finance 5, Administration &amp; Procurement 2. The other 48 are the 46 raised from Gulu and
     * Mbarara, where no staff have departments recorded, plus two Head Office accounts that have
     * none. The page says so, rather than presenting an almost-empty list as though those were all
     * the requests there are.
     */
    private Long requesterDepartmentId;
}
