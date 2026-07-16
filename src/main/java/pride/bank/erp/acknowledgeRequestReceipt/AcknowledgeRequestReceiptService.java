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
import pride.bank.erp.movement.Movement;
import pride.bank.erp.movement.MovementRepository;
import pride.bank.erp.movement.MovementStatus;
import pride.bank.erp.user.User;
import pride.bank.erp.notification.Email;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestDTO;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.request.RequestService;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.workflowinstance.WorkflowEngineService;

import java.util.List;

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
    private final MovementRepository movementRepository;

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

        // Guard: cross-location requests must have their delivery movement actually completed
        // before the requester can acknowledge — otherwise acknowledgement and physical delivery
        // are two disconnected confirmations. DIRECT_ISSUANCE requests have no movement and are
        // unaffected.
        requireDeliveryComplete(request);

        // Persist the audit record first (always, regardless of workflow mode)
        AcknowledgeRequestReceipt acknowledgeRequestReceipt = AcknowledgeRequestReceipt
                .builder()
                .request(request)
                .user(currentUser)
                .comment(acknowledgeRequestReceiptDTO.getComment())
                .build();
        acknowledgeRequestReceiptRepository.save(acknowledgeRequestReceipt);

        if (workflowEngineService.hasActiveInstance(request.getId())) {
            // Workflow mode: engine handles status update, next-step notification and emails
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

    /**
     * If this request was fulfilled via one or more movements (cross-location), the last leg —
     * the one that actually hands the items to the requester ({@code recipientUser != null}) —
     * must be {@code COMPLETED} before receipt can be acknowledged. Requests with no movement rows
     * (same-location / DIRECT_ISSUANCE) are unaffected.
     */
    private void requireDeliveryComplete(Request request) throws BusinessRuleException {
        List<Movement> movements = movementRepository.findByRequest_Id(request.getId());
        if (movements.isEmpty()) {
            return;
        }
        Movement deliveryLeg = movements.stream()
                .filter(m -> m.getRecipientUser() != null)
                .findFirst()
                .orElse(movements.get(movements.size() - 1));

        if (deliveryLeg.getStatus() != MovementStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "The items for this request have not been delivered yet. Please wait until the "
                            + "delivery movement is completed before acknowledging receipt.");
        }
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
