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
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.BadRequestException;
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
        List<ResolvedApprover> chain = resolveChain(movement);
        if (chain.isEmpty()) {
            log.warn("Movement {} requires approval but no approver resolved for initiator {} — rejecting creation",
                    movement.getId(), movement.getInitiator() != null ? movement.getInitiator().getId() : null);
            throw new BadRequestException(
                    "This movement needs approval, but no approver could be resolved on your reporting line. "
                            + "Ask an administrator to set your supervisor before initiating it.");
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
        } else {
            movement.setCurrentApprover(null);
            movement.setStatus(MovementStatus.INITIATED);
            movementRepository.save(movement);
            notifyInitiator(movement, "Movement approved",
                    "Movement #" + movement.getId() + " is fully approved and ready to dispatch.");
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
        notifyInitiator(movement, "Movement rejected",
                "Movement #" + movement.getId() + " was rejected"
                        + (comment != null && !comment.isBlank() ? ": " + comment : "."));
        return movement;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<ResolvedApprover> resolveChain(Movement movement) {
        return (movement.getInitiator() != null)
                ? approverChainResolver.resolveInitiatorChain(movement.getInitiator())
                : List.of();
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

    private void notifyApprover(Movement movement, User approver) {
        if (approver == null) return;
        notificationService.createAndPush(
                approver,
                "Movement Approval Needed",
                "Movement #" + movement.getId() + " is awaiting your approval.",
                "MOVEMENT_PENDING",
                movement.getId(),
                movementLink(movement));
    }

    private void notifyInitiator(Movement movement, String title, String message) {
        if (movement.getInitiator() == null) return;
        notificationService.createAndPush(
                movement.getInitiator(), title, message, "MOVEMENT_UPDATE",
                movement.getId(), movementLink(movement));
    }

    private String movementLink(Movement movement) {
        return frontEndUrl + "/assets-mgt/movement?id=" + movement.getId();
    }
}
