/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.issuanceApprovalRecord;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.ExtractNumberFromEmailAddress;
import pride.bank.erp.issuance.Issuance;
import pride.bank.erp.issuance.IssuanceRepository;
import pride.bank.erp.issuance.IssuanceService;
import pride.bank.erp.issuance.UserBuilder;
import pride.bank.erp.notification.Email;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestDTO;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.workflowinstance.WorkflowEngineService;
import pride.bank.erp.request.RequestService;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;

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
        ExtractNumberFromEmailAddress cleanedEmail = new ExtractNumberFromEmailAddress(email);

        emailService.sendEmailVerification(
                cleanedEmail.getCleanedEmail(), // Notify the requester
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
