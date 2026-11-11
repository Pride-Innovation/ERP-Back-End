/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.acknowledgeRequestReceipt;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.BusinessRuleException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.issuance.UserBuilder;
import pride.bank.erp.user.User;
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
public class AcknowledgeRequestReceiptService {
    private final AcknowledgeRequestReceiptRepository acknowledgeRequestReceiptRepository;
    private final RequestService requestService;
    private final StatusService statusService;
    private final RequestRepository requestRepository;
    private final CurrentUserService currentUserService;
    private final Email emailService;
    private final UserBuilder userBuilder;
    private final WorkflowEngineService workflowEngineService;

    @Transactional
    public RequestDTO createAcknowledgeRequestReceipt(
            AcknowledgeRequestReceiptDTO acknowledgeRequestReceiptDTO
    ) throws NotFoundException, BusinessRuleException {

        Request request = requestService.findRequest(acknowledgeRequestReceiptDTO.getRequestId());

        // Guard: the requester must never acknowledge their own request, even when they
        // belong to the fulfilling unit (Admin/Infra) and hold ACKNOWLEDGE_REQUEST permission.
        // Acknowledgement must be performed by a colleague in the routed unit.
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser != null
                && request.getRequester() != null
                && currentUser.getId().equals(request.getRequester().getId())) {
            throw new BusinessRuleException(
                    "You cannot acknowledge your own request. It must be acknowledged by a colleague in your unit.");
        }

        /*
         * This is the Infra/Admin unit acknowledging the REQUEST, before anything is issued - not the
         * requester acknowledging receipt of the items. The names invite the mix-up, and it happened:
         * the delivery check and the hand-over of issued assets to the requester both used to live
         * here, where there is never a movement or an issued asset yet. So the check always passed
         * and the hand-over always did nothing, and every directly issued asset (Head Office, and a
         * branch issuing from its own store) finished "Issued" with no holder. Both now run at the
         * requester's receipt, in AcknowledgeIssuanceReceiptService.
         */

        // Persist the audit record first (always, regardless of workflow mode)
        AcknowledgeRequestReceipt acknowledgeRequestReceipt = AcknowledgeRequestReceipt
                .builder()
                .request(request)
                .user(currentUser)
                .comment(acknowledgeRequestReceiptDTO.getComment())
                .build();
        acknowledgeRequestReceiptRepository.save(acknowledgeRequestReceipt);

        // Same stage assertion the approval path makes: this service is the Infra/Admin handshake that precedes issuance,
        // and the engine applies whatever action it is handed to whatever step is current.
        workflowEngineService.requireCurrentStepIs(
                request.getId(), pride.bank.erp.workflow.StepType.ACKNOWLEDGE_REQUEST);

        if (workflowEngineService.hasActiveInstance(request.getId())) {
            // Workflow mode: engine handles status update, next-step notification and emails
            assert currentUser != null;
            workflowEngineService.processStepAction(
                    request.getId(),
                    currentUser.getId(),
                    "ACKNOWLEDGED",
                    acknowledgeRequestReceiptDTO.getComment()
            );
        } else {
            // Legacy mode: set status directly and notify requester manually
            Status status = statusService.findOneStatus(acknowledgeRequestReceiptDTO.getStatusId());
            request.setStatus(status);
            requestRepository.save(request);
            notifyIndividual(
                    request.getRequester().getEmail(),
                    acknowledgeRequestReceipt.getComment(),
                    request,
                    request.getRequester().getFirstName()
            );
        }

        // Reload to reflect any status changes made by the engine
        request = requestService.findRequest(acknowledgeRequestReceiptDTO.getRequestId());

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

    public AcknowledgeRequestReceiptDAO findAcknowledgeRequestReceiptByRequestId(Long requestId) {
        AcknowledgeRequestReceipt data = acknowledgeRequestReceiptRepository.findByRequestId(requestId).orElse(null);
        if (data == null) {
            return null;
        }
        return AcknowledgeRequestReceiptDAO.builder()
                .id(data.getId())
                .comment(data.getComment())
                .request(data.getRequest() != null ? Request.builder().build() : null)
                .user(userBuilder.builder(data.getUser()))
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
                email,
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
