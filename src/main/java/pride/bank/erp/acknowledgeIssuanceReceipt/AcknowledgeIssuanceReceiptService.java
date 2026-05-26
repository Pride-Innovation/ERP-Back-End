/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.acknowledgeIssuanceReceipt;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.ExtractNumberFromEmailAddress;
import pride.bank.erp.issuance.UserBuilder;
import pride.bank.erp.notification.Email;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestDTO;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.request.RequestService;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.workflowinstance.WorkflowEngineService;

@Service
@RequiredArgsConstructor
public class AcknowledgeIssuanceReceiptService {
    private final AcknowledgeIssuanceReceiptRepository acknowledgeIssuanceReceiptRepository;
    private final RequestService requestService;
    private final StatusService statusService;
    private final RequestRepository requestRepository;
    private final CurrentUserService currentUserService;
    private final UserBuilder userBuilder;
    private final WorkflowEngineService workflowEngineService;
    private final Email emailService;

    @Transactional
    public RequestDTO createAcknowledgeRequestIssuance(AcknowledgeIssuanceReceiptDTO acknowledgeIssuanceReceiptDTO)
            throws NotFoundException {
        Request request = requestService.findRequest(acknowledgeIssuanceReceiptDTO.getRequestId());

        // Persist the audit record first (always, regardless of workflow mode)
        AcknowledgeIssuanceReceipt acknowledgeIssuanceReceipt = AcknowledgeIssuanceReceipt
                .builder()
                .comment(acknowledgeIssuanceReceiptDTO.getComment())
                .request(request)
                .user(currentUserService.getCurrentUser())
                .build();
        acknowledgeIssuanceReceiptRepository.save(acknowledgeIssuanceReceipt);

        if (workflowEngineService.hasActiveInstance(request.getId())) {
            // Workflow mode: engine handles status update, next-step notification and store update
            workflowEngineService.processStepAction(
                    request.getId(),
                    currentUserService.getCurrentUser().getId(),
                    "ACKNOWLEDGED",
                    acknowledgeIssuanceReceiptDTO.getComment()
            );
        } else {
            // Legacy mode: set status directly and notify requester manually
            Status status = statusService.findOneStatus(acknowledgeIssuanceReceiptDTO.getStatusId());
            request.setStatus(status);
            requestRepository.save(request);
            notifyIndividual(
                    request.getRequester().getEmail(),
                    acknowledgeIssuanceReceiptDTO.getComment(),
                    request,
                    request.getRequester().getFirstName()
            );
        }

        // Reload to reflect any status changes made by the engine
        request = requestService.findRequest(acknowledgeIssuanceReceiptDTO.getRequestId());

        return RequestDTO
                .builder()
                .id(request.getId())
                .name(request.getName())
                .priority(request.getPriority())
                .commodities(request.getCommodities())
                .description(request.getDescription())
                .signaturePath(request.getSignaturePath())
                .status(request.getStatus() != null ?
                        Status.builder()
                                .id(request.getStatus().getId())
                                .name(request.getStatus().getName())
                                .description(request.getStatus().getDescription())
                                .status(request.getStatus().getStatus())
                                .code(request.getStatus().getCode())
                                .build() : null)
                .timeOfSubmissionOfRequest(request.getTimeOfSubmissionOfRequest())
                .createDate(request.getCreateDate())
                .lastModified(request.getLastModified())
                .createdBy(request.getCreatedBy())
                .lastModifiedBy(request.getLastModifiedBy())
                .requester(request.getRequester() != null ?
                        userBuilder.builder(request.getRequester()) : null)
                .currentApprover(request.getCurrentApprover() != null ?
                        userBuilder.builder(request.getCurrentApprover()) : null)
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
                cleanedEmail.getCleanedEmail(),
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

    public AcknowledgeIssuanceReceiptDAO findAcknowledgeIssuanceReceiptByRequestId(Long requestId) {
        AcknowledgeIssuanceReceipt data = acknowledgeIssuanceReceiptRepository.findByRequestId(requestId).orElse(null);

        if (data == null) {
            return null;
        }
        return AcknowledgeIssuanceReceiptDAO.builder()
                .id(data.getId())
                .comment(data.getComment())
                .user(data.getUser() != null ?
                        userBuilder.builder(data.getUser()) : null)
                .createDate(data.getCreateDate())
                .lastModified(data.getLastModified())
                .createdBy(data.getCreatedBy())
                .lastModifiedBy(data.getLastModifiedBy())
                .build();
    }
}
