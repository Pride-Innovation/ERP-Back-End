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
import pride.bank.erp.exception.BusinessRuleException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.issuedAsset.IssuedAssetLifecycleService;
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
    private final IssuedAssetLifecycleService issuedAssetLifecycleService;

    /**
     * The requester confirms the items reached them - the last step of a request.
     *
     * <p>This is where the issued assets become theirs. It used to happen in
     * {@code AcknowledgeRequestReceiptService} - the Infra/Admin unit acknowledging the <em>request</em>,
     * before anything is issued - where there was never an issued asset to hand over. Measured when it
     * was found: every Head Office request since the first, completed and "Receipt Acknowledged", left
     * its laptops, monitors, chairs and tables at "Issued" with no holder and no store. A request
     * delivered by movement was unaffected, because completing the movement assigns the items; a
     * direct issuance - Head Office, or a branch issuing from its own store - has no movement, and
     * this is the only place left to do it.
     */
    @Transactional
    public RequestDTO createAcknowledgeRequestIssuance(AcknowledgeIssuanceReceiptDTO acknowledgeIssuanceReceiptDTO)
            throws NotFoundException, BusinessRuleException {
        Request request = requestService.findRequest(acknowledgeIssuanceReceiptDTO.getRequestId());

        // Delivered by movement: the leg handing the items over must be complete before anyone can say
        // they arrived. This check also lived on the unit's acknowledgement, where no movement exists
        // yet, so it never once refused anything. A direct issuance has no movement and passes.
        requireDeliveryComplete(request);

        // Persist the audit record first (always, regardless of workflow mode)
        AcknowledgeIssuanceReceipt acknowledgeIssuanceReceipt = AcknowledgeIssuanceReceipt
                .builder()
                .comment(acknowledgeIssuanceReceiptDTO.getComment())
                .request(request)
                .user(currentUserService.getCurrentUser())
                .build();
        acknowledgeIssuanceReceiptRepository.save(acknowledgeIssuanceReceipt);

        // Same stage assertion the approval path makes: this service is the requester confirming what arrived,
        // and the engine applies whatever action it is handed to whatever step is current.
        workflowEngineService.requireCurrentStepIs(
                request.getId(), pride.bank.erp.workflow.StepType.ACKNOWLEDGE_RECEIPT);

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

        // The items are the requester's now: each issued asset is assigned to them and reads Assigned.
        // Idempotent, so an asset a delivery movement already assigned is left alone; a store restock's
        // assets stay in the store.
        issuedAssetLifecycleService.markAssigned(request);

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

    /**
     * If this request was fulfilled via one or more movements (cross-location), the last leg - the one
     * that actually hands the items to the requester ({@code recipientUser != null}) - must be
     * {@code COMPLETED} before receipt can be acknowledged. Requests with no movement rows
     * (same-location / DIRECT_ISSUANCE) are unaffected.
     */
    private void requireDeliveryComplete(Request request) throws BusinessRuleException {
        if (!issuedAssetLifecycleService.deliveryComplete(request)) {
            throw new BusinessRuleException(
                    "The items for this request have not been delivered yet. Please wait until the "
                            + "delivery movement is completed before acknowledging receipt.");
        }
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
