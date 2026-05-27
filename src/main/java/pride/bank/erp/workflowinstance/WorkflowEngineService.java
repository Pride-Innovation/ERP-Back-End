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
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.workflow.ApprovalStep;
import pride.bank.erp.workflow.ApprovalWorkflow;
import pride.bank.erp.workflow.ApprovalWorkflowRepository;
import pride.bank.erp.workflow.NotifyGroupSource;
import pride.bank.erp.workflow.StepApproverType;
import pride.bank.erp.workflow.WorkflowBranchScope;
import pride.bank.erp.workflow.WorkflowResolveService;

import java.util.HashSet;
import java.util.List;
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
    private static final String STATUS_ACKNOWLEDGED          = "receiptAcknowledged";

    private final ApprovalWorkflowRepository workflowRepository;
    private final RequestWorkflowInstanceRepository instanceRepository;
    private final RequestStepLogRepository stepLogRepository;
    private final RequestRepository requestRepository;
    private final RequestCommodityRepository requestCommodityRepository;
    private final UserRepository userRepository;
    private final LeaveRepository leaveRepository;
    private final StatusService statusService;
    private final Email emailService;
    private final AppNotificationService notificationService;
    private final WorkflowResolveService workflowResolveService;

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

        // Update the PENDING log for this step
        stepLogRepository.findPendingByInstanceAndStep(instance.getId(), currentOrder)
                .ifPresent(logEntry -> {
                    logEntry.setActor(actor);
                    logEntry.setActorEmail(actor != null ? actor.getEmail() : null);
                    logEntry.setAction(logAction);
                    logEntry.setComment(comment);
                    stepLogRepository.save(logEntry);
                });

        if (logAction == StepLogAction.REJECTED) {
            handleRejection(instance, request, actor, comment);
        } else {
            handleAdvancement(instance, workflow, currentStep, request, actor, comment);
        }
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
     * Returns true if a workflow instance is currently IN_PROGRESS for the given request.
     * Used by downstream services to decide whether to delegate to the engine.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveInstance(Long requestId) {
        return instanceRepository.findTopByRequestIdOrderByIdDesc(requestId)
                .map(i -> i.getStatus() == InstanceStatus.IN_PROGRESS)
                .orElse(false);
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

        User requester = request.getRequester();
        String rejectionComment = (comment != null && !comment.isBlank())
                ? comment
                : "Your request has been rejected.";

        // Track who we have already notified so the same person isn't pinged twice
        // (e.g. the rejector themselves, or the requester appearing as a prior approver
        // in a multi-step chain where they were also a step actor).
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
                request.getId(),
                frontEndUrl + "/assets-mgt/asset-request/view?id=" + request.getId()
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
                    request.getId(),
                    frontEndUrl + "/assets-mgt/asset-request/view?id=" + request.getId()
            );
        } else {
            instance.setCurrentStepOrder(nextStep.getStepOrder());
            instanceRepository.save(instance);
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
            case REQUEST_APPROVAL  -> resolveApprovalStatusCode(step.getApproverType());
            default                -> STATUS_APPROVED;
        };
    }

    /**
     * Maps the approver type of a REQUEST_APPROVAL step to a distinct status code
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
        User approver = resolveApprover(step, request);
        String approverEmail = (approver != null) ? approver.getEmail() : step.getGroupEmail();

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

        // Update currentApprover on request
        if (approver != null) {
            request.setCurrentApprover(approver);
            requestRepository.save(request);
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

        // In-app notification for individual approver
        if (approver != null) {
            notificationService.createAndPush(
                    approver,
                    step.getStepName() + " Needed",
                    "Request \"" + request.getName() + "\" is awaiting your action: " + step.getStepName(),
                    "STEP_PENDING",
                    request.getId(),
                    frontEndUrl + "/assets-mgt/asset-request/view?id=" + request.getId()
            );
        }
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
        List<ApprovalWorkflow> candidates = workflowRepository.findActiveWorkflowsForRequest(
                branchId, assetTypeId, scope, requesterRoleIds);
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

    public User resolveApprover(ApprovalStep step, Request request) {
        User candidate = switch (step.getApproverType()) {
            case DIRECT_SUPERVISOR -> resolveDirectSupervisor(request.getRequester());
            case SUPERVISOR        -> resolveSupervisor(request.getRequester());
            case DEPT_HEAD -> (request.getRequester().getDepartment() != null)
                    ? request.getRequester().getDepartment().getHeadOfDepartment() : null;
            case BOM -> (request.getRequester().getBranch() != null)
                    ? request.getRequester().getBranch().getBranchOperationsManager() : null;
            case BRANCH_MANAGER -> (request.getRequester().getBranch() != null)
                    ? request.getRequester().getBranch().getBranchManager() : null;
            case ADMIN -> userRepository.findByEmail(adminEmail).orElse(null);
            case SPECIFIC_USER -> (step.getSpecificUserId() != null)
                    ? userRepository.findById(step.getSpecificUserId()).orElse(null) : null;
            case GROUP_EMAIL -> null; // no individual approver for group-email-only steps
            case REQUESTER -> request.getRequester();
        };
        return substituteIfOnLeave(candidate);
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

        List<User> supervisors = userRepository.findByTitleIdAndBranchId(supervisorTitleId, branchId);
        return supervisors.isEmpty() ? null : supervisors.get(0);
    }

    /**
     * Resolves the supervisor for a SUPERVISOR-type step.
     * The supervisor is the user whose title is the requester's reportsTo title,
     * but ONLY when that title carries the "SUPERVISOR" role.
     * Returns null when the officer has no supervisor in their chain,
     * which causes shouldSkipStep() to auto-skip the step at runtime.
     */
    private User resolveSupervisor(User requester) {
        if (requester.getTitle() == null || requester.getTitle().getReportsTo() == null) return null;
        Title reportsToTitle = requester.getTitle().getReportsTo();
        if (reportsToTitle.getRole() == null) return null;
        if (!"SUPERVISOR".equalsIgnoreCase(reportsToTitle.getRole().getName())) return null;
        Long branchId = (requester.getBranch() != null) ? requester.getBranch().getId() : null;
        if (branchId == null) return null;
        List<User> candidates = userRepository.findByTitleIdAndBranchId(reportsToTitle.getId(), branchId);
        return candidates.isEmpty() ? null : candidates.get(0);
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
     *
     * Four skip conditions are evaluated in order:
     *
     * 1. SUPERVISOR type — always auto-skip when the officer has no supervisor in their chain.
     *    A SUPERVISOR step with no supervisor to route to is meaningless regardless of the
     *    optional flag.
     *
     * 2. DIRECT_SUPERVISOR type — skip when the officer's immediate reportsTo title carries the
     *    SUPERVISOR role.  Both SUPERVISOR and DIRECT_SUPERVISOR resolve via the same
     *    requester.title.reportsTo link; when that title is a SUPERVISOR, the SUPERVISOR step
     *    (condition 1) already owns that approval tier.  Routing DIRECT_SUPERVISOR to the same
     *    person afterwards would either duplicate the approval or — in the common branch scenario
     *    of Officer → Supervisor → BOM — stall the request by sending it back to the Supervisor
     *    who already approved.  Workflows that need a Supervisor to approve must use the
     *    SUPERVISOR approver type; DIRECT_SUPERVISOR is strictly for when the officer reports
     *    directly to a Manager (no Supervisor tier exists).
     *
     * 3. Optional step — skip any step marked optional=true when its approver cannot be
     *    resolved.  Covers vacant Manager / BOM / Branch-Manager positions: rather than
     *    stalling with currentApprover=null the step is silently bypassed.
     *
     * 4. Role-based skip — requester's effective roles intersect skipIfRequesterRoles.
     */
    private boolean shouldSkipStep(ApprovalStep step, Request request) {
        User requester = request.getRequester();

        // 1. SUPERVISOR auto-skip
        if (step.getApproverType() == StepApproverType.SUPERVISOR
                && resolveSupervisor(requester) == null) {
            return true;
        }

        // 2. DIRECT_SUPERVISOR skip when the officer's reportsTo is a SUPERVISOR title
        if (step.getApproverType() == StepApproverType.DIRECT_SUPERVISOR) {
            Title reportsTo = (requester.getTitle() != null)
                    ? requester.getTitle().getReportsTo() : null;
            if (reportsTo != null
                    && reportsTo.getRole() != null
                    && "SUPERVISOR".equalsIgnoreCase(reportsTo.getRole().getName())) {
                return true;
            }
        }

        // 3. Optional step auto-skip
        if (step.isOptional() && resolveApprover(step, request) == null) {
            return true;
        }

        // 4. Role-based skip
        if (step.getSkipIfRequesterRoles() == null || step.getSkipIfRequesterRoles().isEmpty()) {
            return false;
        }
        Set<Long> requesterRoleIds = requester.getEffectiveRoleIds();
        return step.getSkipIfRequesterRoles().stream()
                .anyMatch(role -> requesterRoleIds.contains(role.getId()));
    }

    /** Returns the first step (by order) that is not skipped for this request. */
    private ApprovalStep getFirstNonSkippedStep(ApprovalWorkflow workflow, Request request) {
        return workflow.getSteps().stream()
                .sorted(java.util.Comparator.comparingInt(ApprovalStep::getStepOrder))
                .filter(step -> !shouldSkipStep(step, request))
                .findFirst()
                .orElse(null);
    }

    /** Returns the next step after {@code currentOrder} that is not skipped for this request. */
    private ApprovalStep getNextNonSkippedStep(ApprovalWorkflow workflow, int currentOrder, Request request) {
        return workflow.getSteps().stream()
                .filter(s -> s.getStepOrder() > currentOrder)
                .sorted(java.util.Comparator.comparingInt(ApprovalStep::getStepOrder))
                .filter(step -> !shouldSkipStep(step, request))
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
        return dto;
    }
}
