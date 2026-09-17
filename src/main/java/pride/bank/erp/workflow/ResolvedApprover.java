/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import pride.bank.erp.user.User;

/**
 * One resolved approval stop produced by {@link ApproverChainResolver} — i.e. a concrete
 * "who acts here" derived from intersecting the workflow's role whitelist with the
 * requester's {@code reportsTo} ladder.
 *
 * <p>A stop is either:
 * <ul>
 *   <li><b>individual</b> — {@link #approver} is set (the in-branch holder of a ladder rung,
 *       or an acting user when the holder is on leave), or</li>
 *   <li><b>group</b> — {@link #groupEmail} is set and {@link #approver} is null. Used when a
 *       Manager rung is unavailable and the request is routed to the managers' group so a peer
 *       can act.</li>
 * </ul>
 *
 * <p>{@link #sourceStep} is the workflow {@code ApprovalStep} that contributed this stop's
 * configuration (name, escalation hours, completion notifications). For the no-intersection
 * {@link #fallback} stop it is the workflow's terminal approval step.
 */
@Data
@Builder
@AllArgsConstructor
public class ResolvedApprover {

    /** The workflow step whose config (name, escalation, notifications) this stop inherits. */
    private ApprovalStep sourceStep;

    /** Name of the whitelisted role this stop satisfies (e.g. "SUPERVISOR"); null for a fallback stop. */
    private String roleName;

    /** Resolved individual approver; null when the stop routes to a group mailbox. */
    private User approver;

    /** Group mailbox to notify; set only when {@link #approver} is null (e.g. managers' group on manager absence). */
    private String groupEmail;

    /** True when this stop came from the no-intersection fallback (immediate reportsTo, branch-capped). */
    private boolean fallback;

    /** Human-readable explanation, surfaced in the workflow preview/tester. */
    private String note;

    /** A group stop notifies a mailbox rather than a named individual. */
    public boolean isGroup() {
        return approver == null && groupEmail != null && !groupEmail.isBlank();
    }
}
