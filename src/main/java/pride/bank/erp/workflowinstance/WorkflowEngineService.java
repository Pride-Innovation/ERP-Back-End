package pride.bank.erp.workflowinstance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.appnotification.AppNotificationService;
import pride.bank.erp.availability.Leave;
import pride.bank.erp.availability.LeaveRepository;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.notification.Email;
import pride.bank.erp.request.Request;
import pride.bank.erp.title.Title;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestCommodity.RequestCommodityRepository;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import org.springframework.security.access.AccessDeniedException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.unit.Unit;
import pride.bank.erp.unit.UnitRepository;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.workflow.ApprovalStep;
import pride.bank.erp.workflow.ApprovalWorkflow;
import pride.bank.erp.workflow.ApprovalWorkflowRepository;
import pride.bank.erp.workflow.ApproverChainResolver;
import pride.bank.erp.workflow.ApproverSubject;
import pride.bank.erp.workflow.NotifyGroupSource;
import pride.bank.erp.workflow.ResolvedApprover;
import pride.bank.erp.workflow.StepApproverType;
import pride.bank.erp.exception.BusinessRuleException;
import pride.bank.erp.workflow.StepType;
import pride.bank.erp.workflow.WorkflowBranchScope;
import pride.bank.erp.workflow.WorkflowResolveService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowEngineService {

    // Status codes (stable across re-seeds)
    private static final String STATUS_REJECTED              = "requestRejected";
    private static final String STATUS_APPROVED              = "requestApproved";
    private static final String STATUS_MANAGER_APPROVED      = "managerApproved";
    private static final String STATUS_HOD_APPROVED          = "hodApproved";
    private static final String STATUS_BOM_APPROVED          = "bomApproved";
    private static final String STATUS_BRANCH_MANAGER_APPROVED = "branchManagerApproved";
    private static final String STATUS_SUPERVISOR_APPROVED     = "supervisorApproved";
    private static final String STATUS_ISSUED                = "issued";
    private static final String STATUS_ISSUANCE_APPROVED     = "issuanceApproved";
    private static final String STATUS_ACKNOWLEDGED          = "receiptAcknowledged";
    private static final String STATUS_UNIT_ACKNOWLEDGED     = "unitAcknowledged";

    private final ApprovalWorkflowRepository workflowRepository;
    private final RequestWorkflowInstanceRepository instanceRepository;
    private final RequestStepLogRepository stepLogRepository;
    private final RequestRepository requestRepository;
    private final RequestCommodityRepository requestCommodityRepository;
    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final LeaveRepository leaveRepository;
    private final StatusService statusService;
    private final Email emailService;
    private final AppNotificationService notificationService;
    private final WorkflowResolveService workflowResolveService;
    private final ApproverChainResolver approverChainResolver;
    private final pride.bank.erp.issuance.IssuanceRepository issuanceRepository;
    private final pride.bank.erp.issuedAsset.IssuedAssetLifecycleService issuedAssetLifecycleService;
    private final pride.bank.erp.auditing.trail.AuditService auditService;
    private final pride.bank.erp.inventory.IssueStockCheckService issueStockCheckService;

    @Value("${application.admin.email}")
    private String adminEmail;

    @Value("${front-end.environment}")
    private String frontEndUrl;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Starts a new workflow instance for the given request.
     * Resolves the best-matching active workflow and notifies the first non-skipped step's approver.
     */
    @Transactional
    public RequestWorkflowInstance startWorkflow(Request request) {
        Long assetTypeId = resolveAssetTypeId(request);
        ApprovalWorkflow workflow = resolveWorkflow(request, assetTypeId);

        if (workflow == null || workflow.getSteps().isEmpty()) {
            // A restock never falls back to the legacy supervisor routing, which knows nothing of
            // where a restock is delivered. It is refused, naming what is missing, and the whole
            // request creation rolls back with it.
            if (request.isForStoreRestock()) {
                throw new IllegalArgumentException("No active workflow handles store-restock requests"
                        + (request.getAssetType() != null ? " for " + request.getAssetType().getName() : "")
                        + ". Ask an administrator to add one under Settings > Approval Workflows "
                        + "(Applies to: Store restock).");
            }
            log.warn("No active workflow found for request {} — falling back to legacy routing", request.getId());
            return null;
        }

        ApprovalStep firstStep = getFirstNonSkippedStep(workflow, request);
        if (firstStep == null) {
            log.warn("All steps are skipped for request {} — auto-completing workflow", request.getId());
            return null;
        }

        RequestWorkflowInstance instance = RequestWorkflowInstance.builder()
                .request(request)
                .workflow(workflow)
                .currentStepOrder(firstStep.getStepOrder())
                .status(InstanceStatus.IN_PROGRESS)
                .build();
        instance = instanceRepository.save(instance);

        notifyAndLogStep(instance, firstStep, request);
        return instance;
    }

    /**
     * Resets a previously rejected workflow instance and restarts from step 1.
     * Called when an officer resubmits an amended request after rejection.
     * The old REJECTED instance is marked SUPERSEDED; a fresh IN_PROGRESS instance is created.
     */
    @Transactional
    public RequestWorkflowInstance resetAndRestartWorkflow(Request request) {
        // Supersede any existing instances for this request
        instanceRepository.findByRequestId(request.getId())
                .forEach(old -> {
                    old.setStatus(InstanceStatus.SUPERSEDED);
                    instanceRepository.save(old);
                });

        // Also clear old pending step logs so the timeline stays clean
        stepLogRepository.findByRequestId(request.getId())
                .stream()
                .filter(l -> l.getAction() == StepLogAction.PENDING)
                .forEach(l -> {
                    l.setAction(StepLogAction.SUPERSEDED);
                    stepLogRepository.save(l);
                });

        return startWorkflow(request);
    }

    /**
     * Records an action on the current workflow step and advances to the next.
     *
     * @param requestId The request ID
     * @param actorId   The ID of the user performing the action (null for system/group steps)
     * @param action    One of: APPROVED, REJECTED, ISSUED, ACKNOWLEDGED
     * @param comment   Optional comment
     */
    @Transactional
    public void processStepAction(Long requestId, Long actorId, String action, String comment) throws NotFoundException {
        Optional<RequestWorkflowInstance> instanceOpt = instanceRepository.findTopByRequestIdOrderByIdDesc(requestId);
        if (instanceOpt.isEmpty()) {
            // No workflow instance — this request was created before the workflow engine was deployed.
            // Legacy fallback: just set status and return.
            log.warn("No workflow instance for request {}; skipping workflow advancement", requestId);
            return;
        }

        RequestWorkflowInstance instance = instanceOpt.get();
        if (instance.getStatus() != InstanceStatus.IN_PROGRESS) {
            log.warn("Workflow for request {} is already {}; ignoring action", requestId, instance.getStatus());
            return;
        }

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request " + requestId + " not found"));

        ApprovalWorkflow workflow = instance.getWorkflow();
        int currentOrder = instance.getCurrentStepOrder();
        ApprovalStep currentStep = getStepByOrder(workflow, currentOrder);

        StepLogAction logAction = parseAction(action);
        User actor = actorId != null ? userRepository.findById(actorId).orElse(null) : null;

        requireActorIsRouted(request, actor, currentStep);

        // Update the PENDING log for this step
        stepLogRepository.findPendingByInstanceAndStep(instance.getId(), currentOrder)
                .ifPresent(logEntry -> {
                    logEntry.setActor(actor);
                    logEntry.setActorEmail(actor != null ? actor.getEmail() : null);
                    logEntry.setAction(logAction);
                    logEntry.setComment(comment);
                    stepLogRepository.save(logEntry);
                });

        /*
         * Recorded here rather than inside handleRejection/handleAdvancement so there is exactly one
         * row per decision. Those methods also run on escalation and on system-driven steps, where
         * nobody decided anything and a row would misattribute the outcome.
         */
        boolean rejected = logAction == StepLogAction.REJECTED;
        auditService.record(
                rejected ? pride.bank.erp.auditing.trail.AuditEventType.REJECTED
                        : pride.bank.erp.auditing.trail.AuditEventType.APPROVED,
                pride.bank.erp.auditing.trail.AuditModule.REQUESTS,
                "REQUEST_" + logAction.name(),
                logAction.name().charAt(0) + logAction.name().substring(1).toLowerCase()
                        + " request #" + requestId + " at step " + currentOrder
                        + (currentStep == null ? "" : " (" + currentStep.getStepName() + ")")
                        + (comment == null || comment.isBlank() ? "" : ": " + comment),
                rejected ? pride.bank.erp.auditing.trail.AuditSeverity.WARNING
                        : pride.bank.erp.auditing.trail.AuditSeverity.INFO,
                "Request", requestId);

        if (rejected) {
            handleRejection(instance, request, actor, comment);
        } else {
            handleAdvancement(instance, workflow, currentStep, request, actor, comment);
        }
    }

    /**
     * Whether this person was ever part of this request's approval.
     *
     * <p>Being routed a request is a <em>durable</em> relationship with it, not a momentary one. The
     * request's {@code currentApprover} only names whoever must act <b>now</b>, and approving moves
     * it to the next tier — so an approver stopped being able to read the record at the instant they
     * approved it, which is when they most want to look at it.
     *
     * <p>Any log naming them counts, whatever its action: a step assigned to them and then escalated
     * past them was still theirs to see.
     */
    @Transactional(readOnly = true)
    public boolean hasActedOnRequest(Long requestId, Long userId) {
        if (requestId == null || userId == null) return false;
        return stepLogRepository.existsByRequestIdAndActorId(requestId, userId);
    }

    /**
     * Returns all step logs for a request (for the approval timeline UI).
     */
    @Transactional(readOnly = true)
    public List<StepLogDTO> getStepLogs(Long requestId) {
        return stepLogRepository.findByRequestId(requestId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Returns the current (most recent) workflow instance for a request, if any.
     */
    @Transactional(readOnly = true)
    public Optional<RequestWorkflowInstance> getInstance(Long requestId) {
        return instanceRepository.findTopByRequestIdOrderByIdDesc(requestId);
    }

    /**
     * Refuses an action that does not belong to the stage the request is actually at.
     *
     * <h2>Why the engine cannot catch this on its own</h2>
     * {@link #processStepAction} resolves whatever step is <em>current</em> and applies the action it
     * is handed. It never asks whether the two suit each other, and it cannot usefully be made to:
     * {@code APPROVED} is legitimate at a {@code REQUEST_APPROVAL} step <b>and</b> at an
     * {@code ISSUANCE} one, because approving an issuance sends the same word. The action vocabulary
     * does not distinguish "approve the request" from "approve the issuance".
     *
     * <p>So the assertion belongs with the caller, which does know which act it is performing. Each
     * service states the step it is for, the way {@code IssuanceApprovalRecordService} already reads
     * {@code StepType.ISSUANCE} for its unit check.
     *
     * <p>What this prevents is not cosmetic. A request at the issuance step still carries an
     * approval-flavoured status ({@code unitAcknowledged}), so an approval call arriving then would
     * have marked the <b>issuance</b> step approved and advanced the workflow — recording the request
     * as issued when nothing had been issued.
     *
     * <p><b>Permissive where the engine is permissive.</b> A request with no active instance predates
     * the workflow engine, and {@code processStepAction} already returns quietly for those. Refusing
     * here would strand work nobody could then clear, which is the failure mode every other guard in
     * this class is careful to avoid.
     */
    @Transactional(readOnly = true)
    public void requireCurrentStepIs(Long requestId, StepType expected) throws NotFoundException {
        Optional<RequestWorkflowInstance> instanceOpt = getInstance(requestId);
        if (instanceOpt.isEmpty() || instanceOpt.get().getStatus() != InstanceStatus.IN_PROGRESS) {
            return;   // legacy or already finished — the engine will no-op too
        }

        RequestWorkflowInstance instance = instanceOpt.get();
        ApprovalStep current = getStepByOrder(instance.getWorkflow(), instance.getCurrentStepOrder());
        if (current == null || current.getStepType() == expected) {
            return;
        }

        log.warn("Refused a {} action on request {}: the workflow is at a {} step ({})",
                expected, requestId, current.getStepType(), current.getStepName());
        throw new BusinessRuleException(
                "This request is at the " + describe(current.getStepType())
                        + " stage, so it cannot be " + describe(expected) + " now.");
    }

    /**
     * Refuses an <em>approval</em> aimed at the issuance sign-off, which has its own endpoint.
     *
     * <h2>Why {@link #requireCurrentStepIs} cannot catch this</h2>
     * The sign-off is stored as a {@code REQUEST_APPROVAL} step — it only differs by carrying
     * {@code approverSubject = ISSUER}, meaning it approves the <em>issuer's</em> issuance rather
     * than the requester's request. So it passes a check for "is this an approval step", and
     * {@code POST /approvals} would advance it exactly like a ladder tier: the request would be
     * recorded {@code issuanceApproved} with <b>nothing sent</b>, because the fulfilment movement is
     * created by {@code POST /approve-issuance} and by nothing else.
     *
     * <p>This is the same shape as the wrong-stage approval that made the guard above necessary, one
     * level finer: there, two rules disagreed about which stage the request was at; here, one stage
     * wears another's name.
     *
     * <p><b>Rejection is deliberately not refused.</b> Refusing an issuance is a real decision and
     * {@code handleRejection} is the only path that records it — there is no reject endpoint beside
     * {@code /approve-issuance}. Blocking it here would leave the stage with no way to say no.
     */
    public void requireNotIssuanceApprovalStage(Long requestId) {
        Optional<RequestWorkflowInstance> instanceOpt = getInstance(requestId);
        if (instanceOpt.isEmpty() || instanceOpt.get().getStatus() != InstanceStatus.IN_PROGRESS) {
            return;   // legacy or already finished — the engine will no-op too
        }

        RequestWorkflowInstance instance = instanceOpt.get();
        ApprovalStep current = getStepByOrder(instance.getWorkflow(), instance.getCurrentStepOrder());
        if (current == null
                || current.getApproverSubject() != pride.bank.erp.workflow.ApproverSubject.ISSUER) {
            return;
        }

        log.warn("Refused a plain approval on request {}: step {} ({}) is the issuance sign-off",
                requestId, instance.getCurrentStepOrder(), current.getStepName());
        throw new BusinessRuleException(
                "This request is awaiting sign-off of its issuance, which has to be approved from "
                        + "the issuance itself so the items are actually sent.");
    }

    /** Step types in the words the people using the system would use. */
    private static String describe(StepType type) {
        if (type == null) return "current";
        return switch (type) {
            case REQUEST_APPROVAL -> "approval";
            case ACKNOWLEDGE_REQUEST -> "acknowledgement";
            case ISSUANCE -> "issuance";
            case ACKNOWLEDGE_RECEIPT -> "receipt acknowledgement";
        };
    }

    /**
     * Returns true if a workflow instance is currently IN_PROGRESS for the given request.
     * Used by downstream services to decide whether to delegate to the engine.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveInstance(Long requestId) {
        return instanceRepository.findTopByRequestIdOrderByIdDesc(requestId)
                .map(i -> i.getStatus() == InstanceStatus.IN_PROGRESS)
                .orElse(false);
    }

    /**
     * Escalation reminder for a single workflow instance. Re-sends the pending step's email
     * to whoever it was originally sent to (the current approver, or a group mailbox) once the
     * step's {@code escalationHours} have elapsed since it became pending — or since the last
     * reminder, so reminders repeat each window until the step is actioned.
     * {@code escalationHours = 0} disables reminders for the step. Runs in its own transaction
     * (called per-instance by {@link WorkflowEscalationScheduler}) so one failure can't abort the sweep.
     */
    @Transactional
    public void sendReminderIfDue(Long instanceId) {
        RequestWorkflowInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null || instance.getStatus() != InstanceStatus.IN_PROGRESS) return;

        RequestStepLog pending = stepLogRepository
                .findPendingByInstanceAndStep(instance.getId(), instance.getCurrentStepOrder())
                .orElse(null);
        if (pending == null) return;

        String recipient = pending.getActorEmail();
        if (recipient == null || recipient.isBlank()) return; // nobody to remind

        ApprovalStep step;
        try {
            step = getStepByOrder(instance.getWorkflow(), instance.getCurrentStepOrder());
        } catch (NotFoundException e) {
            return;
        }

        int escalationHours = step.getEscalationHours();
        if (escalationHours <= 0) return; // escalation disabled for this step

        LocalDateTime since = (pending.getLastReminderAt() != null)
                ? pending.getLastReminderAt()
                : pending.getCreateDate();
        if (since == null || LocalDateTime.now().isBefore(since.plusHours(escalationHours))) {
            return; // not due yet
        }

        Request request = instance.getRequest();
        String greeting = (pending.getActor() != null) ? pending.getActor().getFirstName() : "Team";
        emailService.sendWorkflowEmail(
                recipient,
                "Reminder: " + actionRequiredSubject(step, request),
                greeting,
                step.getStepName() + " – Reminder",
                buildStepInstructions(step, request),
                request,
                step,
                "PENDING"
        );
        pending.setLastReminderAt(LocalDateTime.now());
        stepLogRepository.save(pending);
        log.info("Escalation reminder sent for request {} step {} to {}",
                request.getId(), instance.getCurrentStepOrder(), recipient);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void handleRejection(RequestWorkflowInstance instance, Request request, User actor, String comment) throws NotFoundException {
        instance.setStatus(InstanceStatus.REJECTED);
        instanceRepository.save(instance);

        Status rejectedStatus = statusService.findByStatusCode(STATUS_REJECTED);
        request.setStatus(rejectedStatus);
        request.setCurrentApprover(null);
        requestRepository.save(request);

        // If the rejection lands after items were issued (e.g. issuance approval rejected), return
        // any issued serialized assets to the pool so they can be issued again. No-op otherwise.
        issuedAssetLifecycleService.restoreToAvailable(request);

        User requester = request.getRequester();
        String rejectionComment = (comment != null && !comment.isBlank())
                ? comment
                : "Your request has been rejected.";

        // Track who we have already notified so the same person isn't pinged twice
        // (e.g. the rejector themselves, or the requester appearing as a prior approver
        // in a multistep chain where they were also a step actor).
        Set<Long> notifiedUserIds = new HashSet<>();

        // 1. Requester
        notifyRejection(requester, request, rejectionComment,
                "Your Request Has Been Rejected",
                "Your request \"" + request.getName() + "\" has been rejected.");
        notifiedUserIds.add(requester.getId());

        // Don't fan-out back to the actor who just rejected
        if (actor != null && actor.getId() != null) {
            notifiedUserIds.add(actor.getId());
        }

        // 2. Every previous approver who already APPROVED this request — they
        //    need to know a downstream approver rejected something they cleared.
        List<RequestStepLog> logs = stepLogRepository
                .findByInstanceIdOrderByStepOrderAscCreateDateAsc(instance.getId());
        for (RequestStepLog logEntry : logs) {
            if (logEntry.getAction() != StepLogAction.APPROVED) continue;
            User prev = logEntry.getActor();
            if (prev == null || prev.getId() == null) continue;
            if (!notifiedUserIds.add(prev.getId())) continue;

            notifyRejection(prev, request, rejectionComment,
                    "Request You Approved Was Rejected",
                    "A request you previously approved (\"" + request.getName()
                            + "\") was rejected at a downstream step.");
        }

        // 3. Department managers group email — keeps the wider management team
        //    in the loop without spamming everyone in the department.
        if (requester.getDepartment() != null
                && requester.getDepartment().getManagersGroupEmail() != null
                && !requester.getDepartment().getManagersGroupEmail().isBlank()) {
            emailService.sendWorkflowEmail(
                    requester.getDepartment().getManagersGroupEmail(),
                    "Request Rejected – " + request.getName(),
                    "Team",
                    "Request Rejected",
                    rejectionComment,
                    request,
                    null,
                    "REJECTED"
            );
        }
    }

    /** Single recipient: workflow email + in-app push, with the rejection comment. */
    private void notifyRejection(User user, Request request, String comment,
                                 String emailHeadline, String inAppLeadIn) {
        emailService.sendWorkflowEmail(
                user.getEmail(),
                "Request Rejected – " + request.getName(),
                user.getFirstName(),
                emailHeadline,
                comment,
                request,
                null,
                "REJECTED"
        );
        notificationService.createAndPush(
                user,
                "Request Rejected",
                inAppLeadIn + " " + comment,
                "STEP_REJECTED",
                pride.bank.erp.appnotification.NotificationTarget.REQUEST,
                request.getId()
        );
    }

    private void handleAdvancement(RequestWorkflowInstance instance, ApprovalWorkflow workflow,
                                   ApprovalStep currentStep, Request request, User actor, String comment) throws NotFoundException {
        // Update request status based on current step type
        updateRequestStatus(request, currentStep);

        // Fan out completion notifications configured on the just-finished step
        sendCompletionNotifications(currentStep, request);

        // Find next non-skipped step
        ApprovalStep nextStep = getNextNonSkippedStep(workflow, currentStep.getStepOrder(), request);

        if (nextStep == null) {
            // All steps completed
            instance.setStatus(InstanceStatus.COMPLETED);
            instanceRepository.save(instance);
            request.setCurrentApprover(null);
            requestRepository.save(request);

            // Notify requester that process is complete
            notificationService.createAndPush(
                    request.getRequester(),
                    "Request Process Completed",
                    "Your request \"" + request.getName() + "\" has been fully processed.",
                    "WORKFLOW_COMPLETED",
                    pride.bank.erp.appnotification.NotificationTarget.REQUEST,
                    request.getId()
            );
        } else {
            instance.setCurrentStepOrder(nextStep.getStepOrder());
            instanceRepository.save(instance);

            // Keep the requester informed when their request is approved/recommended and moves
            // forward (CLAUDE.md: notify on approvals as well as rejections). Only for approval
            // stages, and never ping the requester about their own action.
            User requester = request.getRequester();
            boolean actorIsRequester = actor != null && requester != null
                    && actor.getId() != null && actor.getId().equals(requester.getId());
            if (currentStep.getStepType() == StepType.REQUEST_APPROVAL
                    && requester != null && !actorIsRequester) {
                notificationService.createAndPush(
                        requester,
                        "Request Approved",
                        "Your request \"" + request.getName() + "\" was approved at \""
                                + currentStep.getStepName() + "\" and moved to \"" + nextStep.getStepName() + "\".",
                        "STEP_APPROVED",
                        pride.bank.erp.appnotification.NotificationTarget.REQUEST,
                        request.getId()
                );
            }

            notifyAndLogStep(instance, nextStep, request);
        }
    }

    /**
     * Sends a "step-completed" email to every {@link NotifyGroupSource} target
     * configured on the step. Used so the requester gets a "items ready for
     * picking" mail when issuance is approved, and so admin / supervisor get a
     * "items collected" mail once the requester acknowledges receipt.
     * <p>Silent for entries that resolve to a blank email; {@code STATIC} and
     * {@code NONE} are skipped (the per-step CC field already covers static).</p>
     */
    private void sendCompletionNotifications(ApprovalStep step, Request request) {
        Set<NotifyGroupSource> targets = step.getNotifyOnCompletion();
        if (targets == null || targets.isEmpty()) return;

        String subject = String.format("Update – %s completed (Request #%d: %s)",
                step.getStepName(), request.getId(), request.getName());

        for (NotifyGroupSource target : targets) {
            if (target == NotifyGroupSource.STATIC || target == NotifyGroupSource.NONE) continue;
            String email = workflowResolveService.resolveEmailForSource(target, request.getRequester());
            if (email == null) continue;
            emailService.sendWorkflowEmail(
                    email,
                    subject,
                    "Team",
                    step.getStepName() + " completed",
                    buildCompletionInstructions(step, request),
                    request,
                    step,
                    "COMPLETION"
            );
        }
    }

    private String buildCompletionInstructions(ApprovalStep step, Request request) {
        return switch (step.getStepType()) {
            case ISSUANCE -> "Issuance has been approved. The requested items are ready for collection.";
            case ACKNOWLEDGE_RECEIPT -> "The requester has acknowledged receipt of the issued items.";
            case REQUEST_APPROVAL -> "The request has been approved at this stage.";
            case ACKNOWLEDGE_REQUEST -> "The request has been acknowledged and is now being processed.";
        };
    }

    private void updateRequestStatus(Request request, ApprovalStep step) throws NotFoundException {
        String statusCode = resolveStatusCode(step);
        Status status;
        try {
            status = statusService.findByStatusCode(statusCode);
        } catch (NotFoundException e) {
            log.warn("Status '{}' not found — falling back to requestApproved", statusCode);
            status = statusService.findByStatusCode(STATUS_APPROVED);
        }
        request.setStatus(status);
        requestRepository.save(request);
    }

    private String resolveStatusCode(ApprovalStep step) {
        return switch (step.getStepType()) {
            case ISSUANCE          -> STATUS_ISSUED;
            case ACKNOWLEDGE_RECEIPT -> STATUS_ACKNOWLEDGED;
            case ACKNOWLEDGE_REQUEST -> STATUS_UNIT_ACKNOWLEDGED;
            // The issuance-approval step is modeled as a generic REQUEST_APPROVAL, but its
            // approverSubject is ISSUER (it approves the issuer's issuance, not the requester's
            // request). Without this, it would resolve to a plain "*Approved" status and demote the
            // request out of the "Issued" set (issued → issuanceApproved → receiptAcknowledged),
            // hiding it from the Issued tab exactly when the requester needs to acknowledge receipt.
            case REQUEST_APPROVAL  -> step.getApproverSubject() == ApproverSubject.ISSUER
                    ? STATUS_ISSUANCE_APPROVED
                    : resolveApprovalStatusCode(step.getApproverType());
        };
    }

    /**
     * Maps the approver type of REQUEST_APPROVAL step to a distinct status code
     * so each approval stage is uniquely identifiable. Falls back to the generic
     * requestApproved for approver types that don't map to a named role in the chain
     * (GROUP_EMAIL, SPECIFIC_USER, ADMIN, REQUESTER).
     */
    private String resolveApprovalStatusCode(StepApproverType approverType) {
        return switch (approverType) {
            case DIRECT_SUPERVISOR -> STATUS_MANAGER_APPROVED;
            case SUPERVISOR        -> STATUS_SUPERVISOR_APPROVED;
            case DEPT_HEAD         -> STATUS_HOD_APPROVED;
            case BOM               -> STATUS_BOM_APPROVED;
            case BRANCH_MANAGER    -> STATUS_BRANCH_MANAGER_APPROVED;
            default                -> STATUS_APPROVED;
        };
    }

    private void notifyAndLogStep(RequestWorkflowInstance instance, ApprovalStep step, Request request) {
        // Ladder-role approval steps resolve via the shared reportsTo chain (an individual holder,
        // or the managers' group when a manager is unavailable). Everything else keeps the
        // existing per-step resolution.
        ResolvedApprover ladderStop = isLadderApprovalStep(step)
                ? ladderApprovalChain(instance.getWorkflow(), request).get(step.getStepOrder())
                : null;
        User approver = (ladderStop != null) ? ladderStop.getApprover() : resolveApprover(step, request);
        String approverEmail = (ladderStop != null && ladderStop.isGroup())
                ? ladderStop.getGroupEmail()
                : resolveStepRecipientEmail(step, request, approver);
        Unit stepUnit = resolveStepUnit(step);

        // Create pending log
        RequestStepLog pendingLog = RequestStepLog.builder()
                .instance(instance)
                .stepOrder(step.getStepOrder())
                .stepName(step.getStepName())
                .actor(approver)
                .actorEmail(approverEmail)
                .action(StepLogAction.PENDING)
                .build();
        stepLogRepository.save(pendingLog);

        // Point the request at whoever must act now: an individual approver (when there is one)
        // and/or a unit (when the step routes to a unit mailbox). Clearing the other side keeps
        // the inbox accurate — a unit step shouldn't appear assigned to a stale individual.
        request.setCurrentApprover(approver);
        request.setCurrentUnit(stepUnit);
        requestRepository.save(request);

        // A request reaching its Issue step short of stock waits there; say so to the requester and
        // the issuer rather than leaving it to look like an issuer who has not acted. After commit.
        if (step.getStepType() == StepType.ISSUANCE) {
            issueStockCheckService.onIssueStepReached(request.getId());
        }

        // Send email to approver (or group)
        if (approverEmail != null) {
            String greeting = (approver != null) ? approver.getFirstName() : "Team";
            emailService.sendWorkflowEmail(
                    approverEmail,
                    actionRequiredSubject(step, request),
                    greeting,
                    step.getStepName() + " Required",
                    buildStepInstructions(step, request),
                    request,
                    step,
                    "PENDING"
            );
        }

        // CC group email — resolved per the step's notifyGroupSource (STATIC, dynamic, or none)
        String ccEmail = workflowResolveService.resolveCcEmail(step, request.getRequester());
        if (ccEmail != null) {
            emailService.sendWorkflowEmail(
                    ccEmail,
                    "FYI: " + actionRequiredSubject(step, request),
                    "Team",
                    "Request Update – " + step.getStepName(),
                    buildStepInstructions(step, request),
                    request,
                    step,
                    "CC"
            );
        }

        // In-app notification: an individual approver, otherwise every member of the target unit.
        if (approver != null) {
            notificationService.createAndPush(
                    approver,
                    step.getStepName() + " Needed",
                    "Request \"" + request.getName() + "\" is awaiting your action: " + step.getStepName(),
                    "STEP_PENDING",
                    pride.bank.erp.appnotification.NotificationTarget.REQUEST,
                    request.getId()
            );
        } else if (stepUnit != null) {
            for (User member : userRepository.findByUnit_Id(stepUnit.getId())) {
                notificationService.createAndPush(
                        member,
                        step.getStepName() + " Needed",
                        "Request \"" + request.getName() + "\" is awaiting your unit (" + stepUnit.getName() + "): " + step.getStepName(),
                        "STEP_PENDING",
                        pride.bank.erp.appnotification.NotificationTarget.REQUEST,
                        request.getId()
                );
            }
        }
    }

    /**
     * Resolves the unit a step routes to: a {@code GROUP_EMAIL} step whose address matches a
     * unit's group mailbox. Returns null for any other step type or when no unit owns the email.
     */
    private Unit resolveStepUnit(ApprovalStep step) {
        if (step.getApproverType() != StepApproverType.GROUP_EMAIL) return null;
        // Preferred: the step references a unit directly (no string matching, no drift/typos).
        if (step.getUnit() != null) return step.getUnit();
        // Legacy fallback: resolve by the free-text group email.
        String email = step.getGroupEmail();
        if (email == null || email.isBlank()) return null;
        return unitRepository.findByGroupEmailIgnoreCase(email.trim()).orElse(null);
    }

    /**
     * The group address for a step: read live from the selected unit when present (so editing the
     * unit's email on the Units page flows through automatically), otherwise the legacy free text.
     */
    private String stepGroupEmail(ApprovalStep step) {
        if (step.getUnit() != null && notBlank(step.getUnit().getGroupEmail())) {
            return step.getUnit().getGroupEmail();
        }
        return step.getGroupEmail();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Workflow resolution
    // ─────────────────────────────────────────────────────────────────────────

    private ApprovalWorkflow resolveWorkflow(Request request, Long assetTypeId) {
        Long branchId = (request.getRequester().getBranch() != null)
                ? request.getRequester().getBranch().getId() : null;
        boolean isHeadOffice = request.getRequester().getBranch() != null
                && request.getRequester().getBranch().isHeadOffice();
        WorkflowBranchScope scope = isHeadOffice
                ? WorkflowBranchScope.HEAD_OFFICE : WorkflowBranchScope.BRANCH;

        Set<Long> requesterRoleIds = request.getRequester().getEffectiveRoleIds();
        List<ApprovalWorkflow> candidates = pride.bank.erp.workflow.BranchIssuance.servable(
                workflowRepository.findActiveWorkflowsForRequest(
                        branchId, assetTypeId, scope, requesterRoleIds,
                        pride.bank.erp.workflow.WorkflowPurpose.of(request.isForStoreRestock())),
                request.isForStoreRestock());
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private Long resolveAssetTypeId(Request request) {
        // Prefer the explicit primary category set on the request (Phase B)
        if (request.getAssetType() != null) {
            return request.getAssetType().getId();
        }
        // Fallback: derive from the first commodity's asset type (legacy behavior)
        List<RequestCommodity> commodities = requestCommodityRepository.findByRequestId(request.getId());
        if (commodities == null || commodities.isEmpty()) return null;
        RequestCommodity first = commodities.get(0);
        if (first.getCommodity() != null && first.getCommodity().getAssetType() != null) {
            return first.getCommodity().getAssetType().getId();
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Approver resolution
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Refuses an action from someone the step was not routed to.
     *
     * <p>Until now the engine asked only <em>what</em> was being done, never <em>by whom</em>. The
     * route guard checks that the caller holds {@code APPROVE_REQUEST}; nothing then checked that
     * this particular request was waiting on this particular person. Anyone who could approve
     * anything could approve everything, at any tier, including a step routed to a unit they do not
     * belong to — which made the unit inbox a suggestion rather than an assignment.
     *
     * <p>Deliberately permissive where the routing is genuinely ambiguous, because a false refusal
     * here strands a request and the bank's work stops:
     *
     * <ul>
     *   <li><b>No actor</b> — a system-driven or group-email step. Nobody claimed to act, so there
     *       is nothing to verify.</li>
     *   <li><b>Nothing routed</b> — neither an approver nor a unit on the request. Requests created
     *       before the workflow engine are in this state, as are steps whose approver did not
     *       resolve. Refusing would strand them permanently with no way for anyone to clear it.</li>
     *   <li><b>{@code MANAGE_ALL_BRANCH_REQUESTS}</b> — the explicit "may act on any branch's
     *       requests" grant, which is what an override is. The super administrator arrives here
     *       holding it like anyone else, so it needs no special case.</li>
     * </ul>
     *
     * <p>Where routing <em>is</em> explicit it is enforced: the named approver, or a member of the
     * named unit. {@code currentApprover} already accounts for leave substitution, so an acting user
     * standing in for someone away passes on their own account.
     */
    private void requireActorIsRouted(Request request, User actor, ApprovalStep step) {
        if (actor == null) return;

        User routedApprover = request.getCurrentApprover();
        Unit routedUnit = request.getCurrentUnit();
        if (routedApprover == null && routedUnit == null) return;

        if (routedApprover != null && routedApprover.getId() != null
                && routedApprover.getId().equals(actor.getId())) {
            return;
        }

        if (routedUnit != null && routedUnit.getId() != null
                && actor.getUnit() != null
                && routedUnit.getId().equals(actor.getUnit().getId())) {
            return;
        }

        boolean mayActAnywhere = actor.getAuthorities().stream()
                .anyMatch(granted -> Constants.MANAGE_ALL_BRANCH_REQUESTS.equals(granted.getAuthority()));
        if (mayActAnywhere) {
            // Legitimate, but worth a row: someone acted on a step that was not routed to them.
            log.info("User {} acted on request {} at step {} via {} rather than as the routed approver",
                    actor.getId(), request.getId(),
                    step == null ? null : step.getStepOrder(), Constants.MANAGE_ALL_BRANCH_REQUESTS);
            return;
        }

        throw new AccessDeniedException(describeRouting(routedApprover, routedUnit));
    }

    /** Says who the request is actually waiting on, so the refusal is actionable. */
    private static String describeRouting(User routedApprover, Unit routedUnit) {
        if (routedUnit != null) {
            return "This request is with the " + routedUnit.getName()
                    + ", and you are not a member of it.";
        }
        String name = routedApprover == null ? null
                : ((routedApprover.getFirstName() == null ? "" : routedApprover.getFirstName()) + " "
                + (routedApprover.getLastName() == null ? "" : routedApprover.getLastName())).trim();
        return "This request is waiting on "
                + (name == null || name.isEmpty() ? "someone else" : name)
                + ", not on you.";
    }

    public User resolveApprover(ApprovalStep step, Request request) {
        if (isBranchPersonIssuanceStep(step)) {
            return branchIssuanceApprover(step, request);
        }
        // Role-based types climb the SUBJECT's reporting line — the requester by default, or the
        // issuer when the step is configured that way (e.g. issuance approval by the issuer's manager).
        User subject = subjectFor(step, request);
        User candidate = switch (step.getApproverType()) {
            case DIRECT_SUPERVISOR -> resolveDirectSupervisor(subject);
            case SUPERVISOR        -> resolveSupervisor(subject);
            case MANAGER           -> resolveManager(subject);
            case DEPT_HEAD -> (subject.getDepartment() != null)
                    ? subject.getDepartment().getHeadOfDepartment() : null;
            case BOM -> (subject.getBranch() != null)
                    ? subject.getBranch().getBranchOperationsManager() : null;
            case BRANCH_MANAGER -> (subject.getBranch() != null)
                    ? subject.getBranch().getBranchManager() : null;
            case ADMIN -> userRepository.findByEmail(adminEmail).orElse(null);
            case SPECIFIC_USER -> (step.getSpecificUserId() != null)
                    ? userRepository.findById(step.getSpecificUserId()).orElse(null) : null;
            case GROUP_EMAIL -> null; // no individual approver for group-email-only steps
            case REQUESTER -> request.getRequester();
        };
        return substituteIfOnLeave(candidate);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Branch-store issuance (Phase 3, WORKFLOW.md)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * An Issue step, or an issuance-approval step, routed to the branch's BOM or BM: a workflow that
     * keeps a request at its branch and issues it from the branch store. Existing workflows route
     * these steps to a unit or a manager and are unaffected. The rule lives in {@code BranchIssuance},
     * which the choice of workflow for a restock also reads.
     */
    private static boolean isBranchPersonIssuanceStep(ApprovalStep step) {
        return pride.bank.erp.workflow.BranchIssuance.isBranchPersonIssuanceStep(step);
    }

    /**
     * Who acts on a branch issuance step, under maker-checker.
     *
     * <p>Nobody issues their own request, and an issuance is approved by somebody who neither asked
     * for it nor issued it. So the configured role (BOM or BM) is tried first and the other second,
     * skipping anyone excluded: when the BOM raised the request, the BM issues it. When nobody is left
     * the answer is null and the step <b>waits</b> - it is never skipped
     * ({@link #shouldSkipStep}), because skipping an Issue step would skip the issuing. The issuance
     * and approval services refuse the excluded people outright, whatever the routing says.
     *
     * <p>The BOM and BM are found by the branch record's own fields first, and otherwise by who at the
     * branch holds the role: measured when this was written, no branch had either field set, while
     * the people holding the titles were there.
     */
    private User branchIssuanceApprover(ApprovalStep step, Request request) {
        User subject = subjectFor(step, request);
        pride.bank.erp.branch.Branch branch = subject != null ? subject.getBranch() : null;
        if (branch == null) return null;

        java.util.Set<Long> excluded = new java.util.HashSet<>();
        if (request.getRequester() != null) excluded.add(request.getRequester().getId());
        if (step.getStepType() == StepType.REQUEST_APPROVAL) {
            issuanceRepository.findByRequestId(request.getId())
                    .map(pride.bank.erp.issuance.Issuance::getIssuer)
                    .ifPresent(issuer -> excluded.add(issuer.getId()));
        }

        boolean bomFirst = step.getApproverType() == StepApproverType.BOM;
        List<User> candidates = bomFirst
                ? java.util.Arrays.asList(branchRoleHolder(branch, true), branchRoleHolder(branch, false))
                : java.util.Arrays.asList(branchRoleHolder(branch, false), branchRoleHolder(branch, true));
        for (User candidate : candidates) {
            User acting = substituteIfOnLeave(candidate);
            if (acting != null && !excluded.contains(acting.getId())) return acting;
        }
        log.info("Request {}: nobody at {} may act on '{}' - the BOM and BM are its requester or issuer; it waits",
                request.getId(), branch.getName(), step.getStepName());
        return null;
    }

    /** The branch's BOM (or BM): its recorded field, else an active user there holding the role. */
    private User branchRoleHolder(pride.bank.erp.branch.Branch branch, boolean operationsManager) {
        User recorded = operationsManager ? branch.getBranchOperationsManager() : branch.getBranchManager();
        if (recorded != null) return recorded;
        String role = operationsManager ? "BRANCH_OPERATIONS_MANAGER" : "BRANCH_MANAGER";
        return userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && u.getBranch().getId().equals(branch.getId()))
                .filter(u -> u.isEnabled() && !u.isBlocked())
                .filter(u -> holdsRole(u, role))
                .min(java.util.Comparator.comparing(User::getId))
                .orElse(null);
    }

    private static boolean holdsRole(User user, String roleName) {
        java.util.stream.Stream<pride.bank.erp.role.Role> roles = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(user.getTitle() != null ? user.getTitle().getRole() : null),
                java.util.stream.Stream.concat(
                        user.getAdditionalRoles() != null ? user.getAdditionalRoles().stream() : java.util.stream.Stream.empty(),
                        user.getUnit() != null && user.getUnit().getRoles() != null
                                ? user.getUnit().getRoles().stream() : java.util.stream.Stream.empty()));
        return roles.anyMatch(r -> r != null && roleName.equals(r.getName()));
    }

    /**
     * The person whose reporting line a role-based step resolves against. {@code ISSUER} steps use the
     * issuer of the request's issuance; if no issuance exists yet the requester is used as a safe
     * fallback so the request is never stranded.
     */
    private User subjectFor(ApprovalStep step, Request request) {
        if (step.getApproverSubject() == pride.bank.erp.workflow.ApproverSubject.ISSUER) {
            User issuer = issuanceRepository.findByRequestId(request.getId())
                    .map(pride.bank.erp.issuance.Issuance::getIssuer)
                    .orElse(null);
            if (issuer != null) return issuer;
        }
        return request.getRequester();
    }

    /**
     * If the resolved approver is currently on leave and has assigned an acting
     * user, route to the acting user instead. Keeps the request moving while
     * the original approver is away.
     */
    private User substituteIfOnLeave(User candidate) {
        if (candidate == null || candidate.getId() == null) return candidate;
        Optional<Leave> active = leaveRepository.findActiveLeaveByUser(candidate.getId());
        if (active.isPresent() && active.get().getActingUser() != null) {
            return active.get().getActingUser();
        }
        return candidate;
    }

    private User resolveDirectSupervisor(User requester) {
        if (requester.getTitle() == null || requester.getTitle().getReportsTo() == null) {
            return null;
        }
        Long supervisorTitleId = requester.getTitle().getReportsTo().getId();
        Long branchId = (requester.getBranch() != null) ? requester.getBranch().getId() : null;
        if (branchId == null) return null;

        List<User> supervisors = userRepository.findActiveHoldersInBranch(supervisorTitleId, branchId);
        return supervisors.isEmpty() ? null : supervisors.get(0);
    }

    /**
     * Resolves the supervisor for a SUPERVISOR-type step: the nearest ancestor title
     * (walking reportsTo) carrying the SUPERVISOR role, held by a user in the requester's
     * branch. Returns null when the officer has no supervisor in their chain, which causes
     * shouldSkipStep() to auto-skip the step at runtime.
     */
    private User resolveSupervisor(User requester) {
        return firstHolderInBranch(nearestAncestorTitleByRole(requester, "SUPERVISOR"), requester);
    }

    /**
     * Resolves the line manager for a MANAGER-type step: the nearest ancestor title
     * (walking reportsTo) carrying the MANAGER role, held by a user in the requester's
     * branch. Returns null when no such user is found — callers then fall back to the
     * requester's managers-group email so any manager can act.
     */
    private User resolveManager(User requester) {
        return firstHolderInBranch(nearestAncestorTitleByRole(requester, "MANAGER"), requester);
    }

    /**
     * Walks the requester's reporting line (title.reportsTo) upward and returns the
     * nearest ancestor title whose role matches {@code roleName}, or null if none.
     * Cycle- and depth-guarded so malformed title data can never loop forever.
     */
    private Title nearestAncestorTitleByRole(User requester, String roleName) {
        if (requester == null || requester.getTitle() == null) return null;
        Title current = requester.getTitle().getReportsTo();
        Set<Long> visited = new HashSet<>();
        int guard = 0;
        while (current != null && guard++ < 30) {
            if (current.getId() != null && !visited.add(current.getId())) break;
            if (current.getRole() != null && roleName.equalsIgnoreCase(current.getRole().getName())) {
                return current;
            }
            current = current.getReportsTo();
        }
        return null;
    }

    /**
     * The active holder of {@code title} in the requester's branch, oldest account first (no leave
     * substitution here). Never a disabled or blocked account, and the same one every time.
     */
    private User firstHolderInBranch(Title title, User requester) {
        if (title == null || title.getId() == null) return null;
        Long branchId = (requester.getBranch() != null) ? requester.getBranch().getId() : null;
        if (branchId == null) return null;
        List<User> holders = userRepository.findActiveHoldersInBranch(title.getId(), branchId);
        return holders.isEmpty() ? null : holders.get(0);
    }

    /** Managers' group mailbox for the requester: department group at HO, branch group otherwise. */
    private String managersGroupEmail(User requester) {
        return workflowResolveService.resolveEmailForSource(
                NotifyGroupSource.REQUESTER_MANAGERS_GROUP, requester);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step navigation
    // ─────────────────────────────────────────────────────────────────────────

    private ApprovalStep getStepByOrder(ApprovalWorkflow workflow, int order) throws NotFoundException {
        return workflow.getSteps().stream()
                .filter(s -> s.getStepOrder() == order)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Step " + order + " not found in workflow"));
    }

    /**
     * Returns true when this step must be skipped for the given request.
     * <p>
     * Two independent reasons to skip:
     * <p>
     * 1. Role-based skip — the requester themselves holds a role listed in
     *    {@code skipIfRequesterRoles} (e.g. don't make a Branch Manager approve their own
     *    request).
     * <p>
     * 2. No-recipient skip ("skip and continue") — the tier this step targets has nobody to
     *    route to for this particular requester, so the request advances to the next step
     *    instead of stalling. This is what lets a single workflow serve every reporting
     *    shape: a missing Supervisor / Manager / HoD / BOM / Branch-Manager tier is simply
     *    bypassed. See {@link #stepHasRecipient}.
     */
    private boolean shouldSkipStep(ApprovalStep step, Request request, Set<Integer> ladderChainStepOrders) {
        // Ladder-role approval steps are governed entirely by the resolved chain: a step is
        // active only when the resolver placed an approver (or group) on it. This is what skips
        // a tier that is absent from the requester's reporting line, and stops the chain at the
        // workflow's terminal role.
        if (isLadderApprovalStep(step)) {
            return !ladderChainStepOrders.contains(step.getStepOrder());
        }
        // A branch issuance step with nobody to act waits; it is never skipped, because skipping an
        // Issue step would skip the issuing (see branchIssuanceApprover).
        if (isBranchPersonIssuanceStep(step)) {
            return matchesSkipRole(step, request.getRequester());
        }
        return matchesSkipRole(step, request.getRequester())
                || !stepHasRecipient(step, request);
    }

    /** True for a REQUEST_APPROVAL step whose approver is a reporting-line role (resolved via the chain). */
    private boolean isLadderApprovalStep(ApprovalStep step) {
        if (step.getStepType() != StepType.REQUEST_APPROVAL) return false;
        // ISSUER-subject steps resolve against the issuer's line, not the requester chain the
        // shared resolver builds — so keep them out of it and let resolveApprover handle them.
        if (step.getApproverSubject() == pride.bank.erp.workflow.ApproverSubject.ISSUER) return false;
        return switch (step.getApproverType()) {
            case SUPERVISOR, MANAGER, DEPT_HEAD, BOM, BRANCH_MANAGER -> true;
            default -> false; // DIRECT_SUPERVISOR / GROUP_EMAIL / ADMIN / SPECIFIC_USER / REQUESTER
        };
    }

    /**
     * The resolved approval chain keyed by the source step's order. Each chain stop maps 1:1
     * onto the workflow's ladder-role approval step that produced it (the no-intersection
     * fallback stop is carried by the terminal approval step).
     */
    private Map<Integer, ResolvedApprover> ladderApprovalChain(ApprovalWorkflow workflow, Request request) {
        Map<Integer, ResolvedApprover> byOrder = new HashMap<>();
        for (ResolvedApprover stop : approverChainResolver.resolve(request.getRequester(), workflow)) {
            if (stop.getSourceStep() != null) {
                byOrder.put(stop.getSourceStep().getStepOrder(), stop);
            }
        }
        return byOrder;
    }

    /** True when the requester's own role is one the step should bypass. */
    private boolean matchesSkipRole(ApprovalStep step, User requester) {
        if (step.getSkipIfRequesterRoles() == null || step.getSkipIfRequesterRoles().isEmpty()) {
            return false;
        }
        Set<Long> requesterRoleIds = requester.getEffectiveRoleIds();
        return step.getSkipIfRequesterRoles().stream()
                .anyMatch(role -> requesterRoleIds.contains(role.getId()));
    }

    /**
     * True if the step can route to someone for this requester — an individual approver, a
     * static group mailbox, or (for MANAGER) the requester's managers-group. When false the
     * step is skipped and the workflow continues to the next step.
     */
    private boolean stepHasRecipient(ApprovalStep step, Request request) {
        // Role checks climb the same subject the step resolves against (requester or issuer).
        User subject = subjectFor(step, request);
        switch (step.getApproverType()) {
            case MANAGER:
                // Only meaningful when the subject actually has a manager tier in their chain.
                if (nearestAncestorTitleByRole(subject, "MANAGER") == null) return false;
                return resolveApprover(step, request) != null || notBlank(managersGroupEmail(subject));
            case GROUP_EMAIL:
                return notBlank(stepGroupEmail(step));
            case DIRECT_SUPERVISOR: {
                // Legacy semantics: a DIRECT_SUPERVISOR step is owned by a SUPERVISOR step when the
                // immediate report is itself a supervisor, so bypass it in that case.
                Title reportsTo = (subject.getTitle() != null) ? subject.getTitle().getReportsTo() : null;
                if (reportsTo != null && reportsTo.getRole() != null
                        && "SUPERVISOR".equalsIgnoreCase(reportsTo.getRole().getName())) {
                    return false;
                }
                return resolveApprover(step, request) != null;
            }
            default:
                // SUPERVISOR, DEPT_HEAD, BOM, BRANCH_MANAGER, ADMIN, SPECIFIC_USER, REQUESTER
                return resolveApprover(step, request) != null;
        }
    }

    /** The email a step should notify when no individual approver resolved. */
    private String resolveStepRecipientEmail(ApprovalStep step, Request request, User approver) {
        if (approver != null) return approver.getEmail();
        if (step.getApproverType() == StepApproverType.MANAGER) {
            // Manager tier exists but no individual resolved → any manager in the subject's group can act.
            return managersGroupEmail(subjectFor(step, request));
        }
        return stepGroupEmail(step);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Returns the first step (by order) that is not skipped for this request. */
    private ApprovalStep getFirstNonSkippedStep(ApprovalWorkflow workflow, Request request) {
        Set<Integer> chainOrders = ladderApprovalChain(workflow, request).keySet();
        return workflow.getSteps().stream()
                .sorted(java.util.Comparator.comparingInt(ApprovalStep::getStepOrder))
                .filter(step -> !shouldSkipStep(step, request, chainOrders))
                .findFirst()
                .orElse(null);
    }

    /** Returns the next step after {@code currentOrder} that is not skipped for this request. */
    private ApprovalStep getNextNonSkippedStep(ApprovalWorkflow workflow, int currentOrder, Request request) {
        Set<Integer> chainOrders = ladderApprovalChain(workflow, request).keySet();
        return workflow.getSteps().stream()
                .filter(s -> s.getStepOrder() > currentOrder)
                .sorted(java.util.Comparator.comparingInt(ApprovalStep::getStepOrder))
                .filter(step -> !shouldSkipStep(step, request, chainOrders))
                .findFirst()
                .orElse(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private StepLogAction parseAction(String action) {
        try {
            return StepLogAction.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Map legacy statusId-based strings
            return switch (action.toUpperCase()) {
                case "APPROVE", "APPROVED" -> StepLogAction.APPROVED;
                case "REJECT", "REJECTED" -> StepLogAction.REJECTED;
                case "ISSUE", "ISSUED" -> StepLogAction.ISSUED;
                case "ACKNOWLEDGE", "ACKNOWLEDGED" -> StepLogAction.ACKNOWLEDGED;
                default -> StepLogAction.APPROVED;
            };
        }
    }

    private String actionRequiredSubject(ApprovalStep step, Request request) {
        return String.format("Action Required – %s (Request #%d: %s)",
                step.getStepName(), request.getId(), request.getName());
    }

    private String buildStepInstructions(ApprovalStep step, Request request) {
        return switch (step.getStepType()) {
            case REQUEST_APPROVAL -> "Please review and approve or reject this request in the system.";
            case ACKNOWLEDGE_REQUEST -> "Please acknowledge receipt of this request so it can proceed.";
            case ISSUANCE -> "Please proceed to issue the requested items to the requester.";
            case ACKNOWLEDGE_RECEIPT -> "Please confirm receipt of the issued items.";
        };
    }

    private StepLogDTO toDTO(RequestStepLog log) {
        StepLogDTO dto = new StepLogDTO();
        dto.setId(log.getId());
        dto.setStepOrder(log.getStepOrder());
        dto.setStepName(log.getStepName());
        dto.setAction(log.getAction().name());
        dto.setComment(log.getComment());
        dto.setActorEmail(log.getActorEmail());
        dto.setCreateDate(log.getCreateDate());
        dto.setLastModified(log.getLastModified());
        if (log.getActor() != null) {
            dto.setActorId(log.getActor().getId());
            dto.setActorName(log.getActor().getFirstName() + " " + log.getActor().getLastName());
        }

        // Resolve the step definition behind this log so callers get the approver type as an enum
        // rather than having to pattern-match the editable stepName. Read-side only — nothing extra
        // is persisted. A workflow edited after the fact may no longer contain the step order, in
        // which case these stay null rather than guessing.
        ApprovalStep step = findStepDefinition(log);
        if (step != null) {
            if (step.getApproverType() != null) dto.setApproverType(step.getApproverType().name());
            if (step.getStepType() != null) dto.setStepType(step.getStepType().name());
        }
        return dto;
    }

    /** The workflow step a log entry was written against, or null if it can no longer be matched. */
    private ApprovalStep findStepDefinition(RequestStepLog log) {
        RequestWorkflowInstance instance = log.getInstance();
        if (instance == null || instance.getWorkflow() == null) return null;
        return instance.getWorkflow().getSteps().stream()
                .filter(s -> s.getStepOrder() == log.getStepOrder())
                .findFirst()
                .orElse(null);
    }
}
