/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.issuanceApprovalRecord;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.issuance.Issuance;
import pride.bank.erp.issuance.IssuanceRepository;
import pride.bank.erp.issuance.IssuanceService;
import pride.bank.erp.issuance.UserBuilder;
import pride.bank.erp.notification.Email;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestDTO;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.unit.Unit;
import pride.bank.erp.user.User;
import pride.bank.erp.workflow.ApprovalStep;
import pride.bank.erp.workflow.StepType;
import pride.bank.erp.workflowinstance.RequestWorkflowInstance;
import pride.bank.erp.workflowinstance.WorkflowEngineService;
import pride.bank.erp.request.RequestService;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.movement.FulfillmentService;

@Service
@RequiredArgsConstructor
public class IssuanceApprovalRecordService {
    private final IssuanceApprovalRecordRepository issuanceApprovalRecordRepository;
    private final IssuanceService issuanceService;
    private final StatusService statusService;
    private final IssuanceRepository issuanceRepository;
    private final RequestService requestService;
    private final RequestRepository requestRepository;
    private final CurrentUserService currentUserService;
    private final Email emailService;
    private final UserBuilder userBuilder;
    private final WorkflowEngineService workflowEngineService;
    private final FulfillmentService fulfillmentService;

    /**
     * Creates an approval record for an issuance and updates the issuance status to "Approved".
     *
     * @param issuanceApprovalRecordDTO DTO containing the issuance ID and optional approval comment
     * @return the saved IssuanceApprovalRecord
     * @throws NotFoundException if the referenced issuance or status is not found
     */
    @Transactional
    public RequestDTO createIssuanceApprovalRecord(IssuanceApprovalRecordDTO issuanceApprovalRecordDTO)
            throws NotFoundException {

        Issuance issuance = issuanceService.findOne(issuanceApprovalRecordDTO.getIssuanceId());
        Request request = requestService.findRequest(issuanceApprovalRecordDTO.getRequestId());

        // Route-level security already requires APPROVE_ISSUANCE (see SecurityConfiguration).
        // This adds a second, finer-grained check: the ISSUER must have belonged to the Unit
        // this request's workflow routes issuance through (e.g. Admin Unit for an Admin-handled
        // workflow). Checked on the issuer, not the approver — the approver is resolved via the
        // issuer's own reporting ladder (a Manager, not a unit member), so a unit requirement on
        // the approver would make issuance approval unreachable for every manager in practice.
        requireIssuerUnitMatch(request, issuance);

        // Persist the audit record (always, regardless of workflow mode)
        IssuanceApprovalRecord record = IssuanceApprovalRecord.builder()
                .issuance(issuance)
                .request(request)
                .user(currentUserService.getCurrentUser())
                .comment(issuanceApprovalRecordDTO.getComment())
                .build();
        issuanceApprovalRecordRepository.save(record);

        if (workflowEngineService.hasActiveInstance(request.getId())) {
            // Workflow mode: engine handles status update and next-step notification
            workflowEngineService.processStepAction(
                    request.getId(),
                    currentUserService.getCurrentUser().getId(),
                    "APPROVED",
                    issuanceApprovalRecordDTO.getComment()
            );
        } else {
            // Legacy mode: set status directly and notify requester manually
            Status approvedIssuanceStatus = statusService.findOneStatus(issuanceApprovalRecordDTO.getStatusId());
            issuance.setStatus(approvedIssuanceStatus);
            request.setStatus(approvedIssuanceStatus);
            issuanceRepository.save(issuance);
            requestRepository.save(request);
            notifyIndividual(
                    request.getRequester().getEmail(),
                    issuanceApprovalRecordDTO.getComment(),
                    request,
                    request.getRequester().getFirstName()
            );
        }

        // §6 fulfilment decision: same location → direct issuance (already handled); different
        // locations → record an inter-location movement for tracking. Never blocks the approval.
        fulfillmentService.handleIssuanceApproval(request, issuance);

        // Reload to reflect any status changes made by the engine
        Request response = requestService.findRequest(issuanceApprovalRecordDTO.getRequestId());

        return RequestDTO
                .builder()
                .id(response.getId())
                .name(response.getName())
                .priority(response.getPriority())
                .commodities(response.getCommodities())
                .description(response.getDescription())
                .signaturePath(response.getSignaturePath())
                .status(response.getStatus() != null ?
                        Status.builder()
                                .id(response.getStatus().getId())
                                .name(response.getStatus().getName())
                                .description(response.getStatus().getDescription())
                                .status(response.getStatus().getStatus())
                                .code(response.getStatus().getCode())
                                .build() : null)
                .timeOfSubmissionOfRequest(response.getTimeOfSubmissionOfRequest())
                .createDate(response.getCreateDate())
                .lastModified(response.getLastModified())
                .createdBy(response.getCreatedBy())
                .lastModifiedBy(response.getLastModifiedBy())
                .requester(response.getRequester() != null ?
                        userBuilder.builder(response.getRequester()) : null)
                .currentApprover(response.getCurrentApprover() != null ?
                        userBuilder.builder(response.getCurrentApprover()) : null)
                .build();
    }

    /**
     * Enforces that the {@code issuance}'s issuer belonged to the Unit configured on this
     * request's {@code ISSUANCE} workflow step (e.g. Admin Unit for an Admin-handled workflow).
     * Skips (permissive) when the request has no active workflow instance, its ISSUANCE step has
     * no unit configured, or the issuer has no unit — legacy/unconfigured cases this was never
     * meant to block.
     */
    private void requireIssuerUnitMatch(Request request, Issuance issuance) throws NotFoundException {
        RequestWorkflowInstance instance = workflowEngineService.getInstance(request.getId()).orElse(null);
        if (instance == null || instance.getWorkflow() == null) {
            return;
        }

        Unit requiredUnit = instance.getWorkflow().getSteps().stream()
                .filter(step -> step.getStepType() == StepType.ISSUANCE)
                .map(ApprovalStep::getUnit)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (requiredUnit == null) {
            return;
        }

        User issuer = issuance.getIssuer();
        Unit issuerUnit = issuer != null ? issuer.getUnit() : null;
        if (issuerUnit == null || !issuerUnit.getId().equals(requiredUnit.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This issuance was not made by a member of " + requiredUnit.getName() + "; it cannot be approved.");
        }
    }

    public IssuanceApprovalRecordDAO findIssuanceApprovalRecordByRequestId(Long requestId) {
        IssuanceApprovalRecord data = issuanceApprovalRecordRepository
                .findByRequestId(requestId).orElse(null);

        if (data == null) {
            return null;
        }

        return IssuanceApprovalRecordDAO.builder()
                .id(data.getId())
                .comment(data.getComment())
                .issuance(data.getIssuance() != null ?
                        Issuance.builder().build() : null)
                .user(data.getUser() != null ?
                        userBuilder.builder(data.getUser()) : null)
                .createDate(data.getCreateDate())
                .lastModified(data.getLastModified())
                .createdBy(data.getCreatedBy())
                .lastModifiedBy(data.getLastModifiedBy())
                .build();
    }


    private void notifyIndividual(
            String email,
            String comment,
            Request request,
            String requesterName
    ) {
        emailService.sendEmailVerification(
                email, // Notify the requester
                String.format("Stores Request ID: %s", request.getId()),
                null,
                requesterName,
                "View Request",
                comment,
                "requests",
                request,
                null,
                null,
                null,
                null
        );
    }
}
