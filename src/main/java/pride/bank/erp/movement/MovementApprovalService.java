/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.appnotification.AppNotificationService;
import pride.bank.erp.auditing.trail.AuditEventType;
import pride.bank.erp.auditing.trail.AuditModule;
import pride.bank.erp.auditing.trail.AuditSeverity;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.exception.NoApproverException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.user.User;
import pride.bank.erp.workflow.ApproverChainResolver;
import pride.bank.erp.workflow.ResolvedApprover;

import java.util.List;

/**
 * Approval routing for standalone movements (CLAUDE.md — movements not fulfilling an already-approved
 * request must be approved "like a request"). Resolves the initiator's full reporting ladder via
 * {@link ApproverChainResolver#resolveInitiatorChain} and walks it tier-by-tier: the movement sits in
 * {@link MovementStatus#DRAFT} until the last tier clears, then flips to {@link MovementStatus#INITIATED}
 * (ready to dispatch). Rejection cancels the movement and notifies the initiator.
 *
 * <p>The chain is re-resolved on each decision (deterministic — same initiator ladder), so the
 * position is tracked by {@code approvalTierIndex} on the movement rather than a stored list.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovementApprovalService {

    private final MovementRepository movementRepository;
    private final MovementApprovalRecordRepository recordRepository;
    private final ApproverChainResolver approverChainResolver;
    private final CurrentUserService currentUserService;
    private final AppNotificationService notificationService;
    /** Mail is the redundant channel — an approver who is not logged in has no other prompt. */
    private final pride.bank.erp.notification.Email email;
    private final pride.bank.erp.auditing.trail.AuditService auditService;

    @Value("${front-end.environment}")
    private String frontEndUrl;

    /**
     * Parks a freshly-created standalone movement in DRAFT and notifies the first ladder tier.
     *
     * <p>If no approver resolves on the initiator's reporting line this fails the whole creation.
     * It previously fell back to INITIATED, which meant a movement flagged as requiring approval
     * proceeded with none at all — and silently, so nobody could tell an approved movement from an
     * unapproved one. Anyone at the top of their own ladder (or with {@code reportsTo} unset) could
     * move stock between locations unchecked. An empty chain is a configuration fault, not a
     * business case, so it is surfaced as one; the caller's transaction rolls back and no movement
     * is left behind.
     */
    @Transactional
    public void startApproval(Movement movement) {
        startApproval(movement, false, null);
    }

    /**
     * @param proceedWithoutApproval the initiator has been told no approver could be found and has
     *                               chosen to continue regardless
     * @param bypassReason           why — recorded on the trail, and worth insisting on
     */
    @Transactional
    public void startApproval(Movement movement, boolean proceedWithoutApproval, String bypassReason) {
        ApproverChainResolver.InitiatorChain resolved = resolveChainDetailed(movement);
        List<ResolvedApprover> chain = resolved.stops();
        if (chain.isEmpty()) {
            if (!proceedWithoutApproval) {
                log.warn("Movement {} requires approval but no approver resolved for initiator {} — "
                                + "rejecting creation. Reason: {}",
                        movement.getId(), movement.getInitiator() != null ? movement.getInitiator().getId() : null,
                        resolved.emptyReason());
                // The specific fault is carried in the message rather than left to the reader to
                // guess at: "no approver" covers a missing branch, a missing title, a title that
                // reports to nothing and a vacant or absent rung, and each wants a different fix.
                throw new NoApproverException(
                        "This movement needs approval, but no approver could be resolved on your reporting line. "
                                + resolved.emptyReason()
                                + " Ask an administrator to correct this, or confirm that you want to "
                                + "proceed without approval.");
            }
            // Proceeding, but on the record. The BYPASSED tier names who authorised it and why, so
            // the movement can never be mistaken later for one that cleared a ladder.
            movement.setStatus(MovementStatus.INITIATED);
            movement.setCurrentApprover(null);
            movement.setApprovalBypassed(true);
            movementRepository.save(movement);
            // The diagnosis goes on the trail too — a year from now "no approver on the line" says
            // nothing about whether the line was broken or genuinely empty.
            record(movement, currentUserService.getCurrentUser(), MovementApprovalAction.BYPASSED,
                    "No approver on the initiator's line", 0,
                    clampComment(bypassReason + " [" + resolved.emptyReason() + "]"));
            log.warn("Movement {} proceeded WITHOUT approval — confirmed by user {} ({})",
                    movement.getId(),
                    currentUserService.getCurrentUser() != null ? currentUserService.getCurrentUser().getId() : null,
                    bypassReason);
            // Critical, and the reason this module was instrumented first. A movement that skipped
            // its ladder is indistinguishable on the list from one that cleared it; this row is what
            // tells them apart to anyone not reading the approval trail itself.
            audit(AuditEventType.SYSTEM, "MOVEMENT_APPROVAL_BYPASSED", movement,
                    "Movement #" + movement.getId() + " proceeded WITHOUT approval — "
                            + bypassReason + " [" + resolved.emptyReason() + "]",
                    AuditSeverity.CRITICAL);
            return;
        }
        ResolvedApprover first = chain.get(0);
        movement.setStatus(MovementStatus.DRAFT);
        movement.setApprovalTierIndex(0);
        movement.setCurrentApprover(first.getApprover());
        movementRepository.save(movement);
        record(movement, first.getApprover(), MovementApprovalAction.PENDING, first.getRoleName(), 0, null);
        notifyApprover(movement, first.getApprover());
    }

    @Transactional
    public Movement approve(Long movementId, String comment) throws NotFoundException {
        Movement movement = find(movementId);
        User actor = currentUserService.getCurrentUser();
        guardActionable(movement, actor);

        record(movement, actor, MovementApprovalAction.APPROVED, roleOf(movement), movement.getApprovalTierIndex(), comment);

        List<ResolvedApprover> chain = resolveChain(movement);
        int next = movement.getApprovalTierIndex() + 1;
        if (next < chain.size()) {
            ResolvedApprover tier = chain.get(next);
            movement.setApprovalTierIndex(next);
            movement.setCurrentApprover(tier.getApprover());
            movementRepository.save(movement);
            record(movement, tier.getApprover(), MovementApprovalAction.PENDING, tier.getRoleName(), next, null);
            notifyApprover(movement, tier.getApprover());
            audit(AuditEventType.APPROVED, "MOVEMENT_APPROVED_TIER", movement,
                    "Approved movement #" + movement.getId() + " at tier " + (next) + " — now with "
                            + tier.getRoleName() + (isBlank(comment) ? "" : ": " + comment),
                    AuditSeverity.INFO);
        } else {
            movement.setCurrentApprover(null);
            movement.setStatus(MovementStatus.INITIATED);
            movementRepository.save(movement);
            notifyInitiatorOfOutcome(movement, "Movement approved",
                    "Movement #" + movement.getId() + " is fully approved and ready to dispatch.",
                    MovementApprovalAction.APPROVED, comment);
            audit(AuditEventType.APPROVED, "MOVEMENT_APPROVED_FINAL", movement,
                    "Gave final approval to movement #" + movement.getId()
                            + " — cleared for dispatch" + (isBlank(comment) ? "" : ": " + comment),
                    AuditSeverity.INFO);
        }
        return movement;
    }

    @Transactional
    public Movement reject(Long movementId, String comment) throws NotFoundException {
        Movement movement = find(movementId);
        User actor = currentUserService.getCurrentUser();
        guardActionable(movement, actor);

        record(movement, actor, MovementApprovalAction.REJECTED, roleOf(movement), movement.getApprovalTierIndex(), comment);
        movement.setStatus(MovementStatus.CANCELLED);
        movement.setCurrentApprover(null);
        if (comment != null && !comment.isBlank()) {
            movement.setRemarks(comment);
        }
        movementRepository.save(movement);
        notifyInitiatorOfOutcome(movement, "Movement rejected",
                "Movement #" + movement.getId() + " was rejected"
                        + (comment != null && !comment.isBlank() ? ": " + comment : "."),
                MovementApprovalAction.REJECTED, comment);
        audit(AuditEventType.REJECTED, "MOVEMENT_REJECTED", movement,
                "Rejected movement #" + movement.getId() + " — cancelled"
                        + (isBlank(comment) ? "" : ": " + comment),
                AuditSeverity.WARNING);
        return movement;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Keeps a trail comment inside {@code MovementApprovalRecord.comment}'s 1000 characters.
     *
     * <p>The bypass reason is free text the user types, and the diagnosis is appended to it. This is
     * the one path that must not fail on a write: it is reached only after the user has been told
     * approval is impossible and has chosen to proceed, so losing the movement to an oversized
     * column would be the worst possible moment to be strict about it.
     */
    private static String clampComment(String comment) {
        if (comment == null || comment.length() <= 1000) {
            return comment;
        }
        return comment.substring(0, 997) + "...";
    }

    /** Records an approval event under the Movement module, tagged to the movement itself. */
    private void audit(AuditEventType eventType, String action, Movement movement,
                       String description, AuditSeverity severity) {
        auditService.record(eventType, AuditModule.MOVEMENT, action, description, severity,
                "Movement", movement.getId());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<ResolvedApprover> resolveChain(Movement movement) {
        return resolveChainDetailed(movement).stops();
    }

    /** As {@link #resolveChain}, but keeping the resolver's explanation for an empty result. */
    private ApproverChainResolver.InitiatorChain resolveChainDetailed(Movement movement) {
        return approverChainResolver.resolveInitiatorChainDetailed(movement.getInitiator());
    }

    private void guardActionable(Movement movement, User actor) {
        if (movement.getStatus() != MovementStatus.DRAFT) {
            throw new BadRequestException("This movement is not awaiting approval.");
        }
        User current = movement.getCurrentApprover();
        if (current != null && actor != null && current.getId() != null
                && !current.getId().equals(actor.getId())) {
            throw new BadRequestException("This movement is awaiting approval by another user.");
        }
    }

    private String roleOf(Movement movement) {
        List<ResolvedApprover> chain = resolveChain(movement);
        int i = movement.getApprovalTierIndex();
        return (i >= 0 && i < chain.size()) ? chain.get(i).getRoleName() : null;
    }

    private Movement find(Long id) throws NotFoundException {
        // findDetailById, never findById — see MovementRepository#findDetailById for why.
        return movementRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException("Movement " + id + " not found"));
    }

    private void record(Movement movement, User actor, MovementApprovalAction action, String tierRole, int tierIndex, String comment) {
        recordRepository.save(MovementApprovalRecord.builder()
                .movement(movement)
                .actor(actor)
                .action(action)
                .tierRole(tierRole)
                .tierIndex(tierIndex)
                .comment(comment)
                .createDate(java.time.LocalDateTime.now())
                .build());
    }

    /**
     * Tells the tier now holding the decision, in-app and by email.
     *
     * <p>Mail matters here more than elsewhere: the movement list no longer carries a "pending my
     * approval" table, so an approver who is not logged in has no other prompt. The in-app
     * notification is written first and does not depend on mail succeeding.
     */
    private void notifyApprover(Movement movement, User approver) {
        if (approver == null) return;
        notificationService.createAndPush(
                approver,
                "Movement Approval Needed",
                "Movement #" + movement.getId() + " is awaiting your approval.",
                "MOVEMENT_PENDING",
                movement.getId(),
                movementLink(movement));

        email.sendMovementEmail(
                approver.getEmail(),
                displayName(approver),
                movement.getId(),
                typeLabel(movement),
                routeLabel(movement),
                movementLink(movement),
                null,   // no outcome yet — this is the request for a decision
                null);
    }

    /**
     * Tells the initiator what the ladder decided. Only fires on the final outcome — approved or
     * rejected — not on each tier passing it along, which would be noise for someone who only needs
     * to know whether they can dispatch.
     */
    private void notifyInitiator(Movement movement, String title, String message) {
        if (movement.getInitiator() == null) return;
        notificationService.createAndPush(
                movement.getInitiator(), title, message, "MOVEMENT_UPDATE",
                movement.getId(), movementLink(movement));
    }

    /** As above, plus the mail carrying the decision and whatever the approver said about it. */
    private void notifyInitiatorOfOutcome(Movement movement, String title, String message,
                                          MovementApprovalAction outcome, String comment) {
        notifyInitiator(movement, title, message);
        if (movement.getInitiator() == null) return;
        email.sendMovementEmail(
                movement.getInitiator().getEmail(),
                displayName(movement.getInitiator()),
                movement.getId(),
                typeLabel(movement),
                routeLabel(movement),
                movementLink(movement),
                outcome.name(),
                comment);
    }

    /** Read on this thread, where the session is open — see {@code Email#sendMovementEmail}. */
    private static String typeLabel(Movement movement) {
        return movement.getMovementType() == null ? null
                : movement.getMovementType().name().replace('_', ' ').toLowerCase();
    }

    /** "Head Office IT Store → Gulu Branch", with either end possibly being a person. */
    private static String routeLabel(Movement movement) {
        String from = movement.getSourceStore() != null
                ? movement.getSourceStore().getName()
                : displayName(movement.getSourceUser());
        String to = movement.getDestStore() != null
                ? movement.getDestStore().getName()
                : displayName(movement.getRecipientUser());
        if (from == null && to == null) return null;
        return (from == null ? "—" : from) + " → " + (to == null ? "—" : to);
    }

    private static String displayName(User user) {
        if (user == null) return null;
        String name = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
        return name.isEmpty() ? null : name;
    }

    /**
     * Deep link to the movement itself, not the list.
     *
     * <p>The route is {@code /assets-mgt/movement/view/:id}. This previously built
     * {@code /assets-mgt/movement?id=N}, and nothing on the list page reads that query param — so
     * every approval email and bell notification dropped the approver on the full list to hunt for
     * the movement by hand. That matters more now that the "pending my approval" table is gone: the
     * link is the route to the decision.
     */
    private String movementLink(Movement movement) {
        return frontEndUrl + "/assets-mgt/movement/view/" + movement.getId();
    }
}
