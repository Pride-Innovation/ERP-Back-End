package pride.bank.erp.workflowinstance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.appnotification.AppNotificationService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.notification.Email;
import pride.bank.erp.request.Request;
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
import pride.bank.erp.workflow.WorkflowBranchScope;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowEngineService {

    private static final Long HEAD_OFFICE_BRANCH_ID = 1L;

    // Status IDs (from existing system)
    private static final Long STATUS_REJECTED = 2L;
    private static final Long STATUS_APPROVED = 3L;
    private static final Long STATUS_ISSUED = 5L;
    private static final Long STATUS_ACKNOWLEDGED = 7L;

    private final ApprovalWorkflowRepository workflowRepository;
    private final RequestWorkflowInstanceRepository instanceRepository;
    private final RequestStepLogRepository stepLogRepository;
    private final RequestRepository requestRepository;
    private final RequestCommodityRepository requestCommodityRepository;
    private final UserRepository userRepository;
    private final StatusService statusService;
    private final Email emailService;
    private final AppNotificationService notificationService;

    @Value("${application.admin.email}")
    private String adminEmail;

    @Value("${front-end.environment}")
    private String frontEndUrl;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Starts a new workflow instance for the given request.
     * Resolves the best-matching active workflow and notifies step 1's approver.
     */
    @Transactional
    public RequestWorkflowInstance startWorkflow(Request request) {
        Long assetTypeId = resolveAssetTypeId(request);
        ApprovalWorkflow workflow = resolveWorkflow(request, assetTypeId);

        if (workflow == null || workflow.getSteps().isEmpty()) {
            log.warn("No active workflow found for request {} — falling back to legacy routing", request.getId());
            return null;
        }

        RequestWorkflowInstance instance = RequestWorkflowInstance.builder()
                .request(request)
                .workflow(workflow)
                .currentStepOrder(1)
                .status(InstanceStatus.IN_PROGRESS)
                .build();
        instance = instanceRepository.save(instance);

        notifyAndLogStep(instance, workflow.getSteps().get(0), request);
        return instance;
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
        Optional<RequestWorkflowInstance> instanceOpt = instanceRepository.findByRequestId(requestId);
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
     * Returns the current workflow instance for a request, if any.
     */
    @Transactional(readOnly = true)
    public Optional<RequestWorkflowInstance> getInstance(Long requestId) {
        return instanceRepository.findByRequestId(requestId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void handleRejection(RequestWorkflowInstance instance, Request request, User actor, String comment) throws NotFoundException {
        instance.setStatus(InstanceStatus.REJECTED);
        instanceRepository.save(instance);

        Status rejectedStatus = statusService.findOneStatus(STATUS_REJECTED);
        request.setStatus(rejectedStatus);
        request.setCurrentApprover(null);
        requestRepository.save(request);

        // Notify requester
        User requester = request.getRequester();
        String rejectionComment = comment != null ? comment : "Your request has been rejected.";
        emailService.sendWorkflowEmail(
                requester.getEmail(),
                "Request Rejected – " + request.getName(),
                requester.getFirstName(),
                "Your Request Has Been Rejected",
                rejectionComment,
                request,
                null,
                "REJECTED"
        );
        notificationService.createAndPush(
                requester,
                "Request Rejected",
                "Your request \"" + request.getName() + "\" has been rejected. " + rejectionComment,
                "STEP_REJECTED",
                request.getId(),
                frontEndUrl + "/assets-mgt/asset-request/view?id=" + request.getId()
        );
    }

    private void handleAdvancement(RequestWorkflowInstance instance, ApprovalWorkflow workflow,
                                   ApprovalStep currentStep, Request request, User actor, String comment) throws NotFoundException {
        // Update request status based on current step type
        updateRequestStatus(request, currentStep);

        // Find next step
        ApprovalStep nextStep = getNextStep(workflow, currentStep.getStepOrder());

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

    private void updateRequestStatus(Request request, ApprovalStep step) throws NotFoundException {
        Long statusId;
        switch (step.getStepType()) {
            case ISSUANCE -> statusId = STATUS_ISSUED;
            case ACKNOWLEDGE_RECEIPT -> statusId = STATUS_ACKNOWLEDGED;
            default -> statusId = STATUS_APPROVED;
        }
        Status status = statusService.findOneStatus(statusId);
        request.setStatus(status);
        requestRepository.save(request);
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

        // CC group email
        if (step.getNotifyGroupEmail() != null && !step.getNotifyGroupEmail().isBlank()) {
            emailService.sendWorkflowEmail(
                    step.getNotifyGroupEmail(),
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
        WorkflowBranchScope scope = HEAD_OFFICE_BRANCH_ID.equals(branchId)
                ? WorkflowBranchScope.HEAD_OFFICE : WorkflowBranchScope.BRANCH;

        List<ApprovalWorkflow> candidates = workflowRepository.findActiveWorkflowsForRequest(
                branchId, assetTypeId, scope);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private Long resolveAssetTypeId(Request request) {
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
        return switch (step.getApproverType()) {
            case DIRECT_SUPERVISOR -> resolveDirectSupervisor(request.getRequester());
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
        };
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

    // ─────────────────────────────────────────────────────────────────────────
    // Step navigation
    // ─────────────────────────────────────────────────────────────────────────

    private ApprovalStep getStepByOrder(ApprovalWorkflow workflow, int order) throws NotFoundException {
        return workflow.getSteps().stream()
                .filter(s -> s.getStepOrder() == order)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Step " + order + " not found in workflow"));
    }

    private ApprovalStep getNextStep(ApprovalWorkflow workflow, int currentOrder) {
        return workflow.getSteps().stream()
                .filter(s -> s.getStepOrder() > currentOrder)
                .min((a, b) -> Integer.compare(a.getStepOrder(), b.getStepOrder()))
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
