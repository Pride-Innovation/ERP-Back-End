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

    @Transactional
    public RequestDTO createAcknowledgeRequestReceipt(
            AcknowledgeRequestReceiptDTO acknowledgeRequestReceiptDTO
    ) throws NotFoundException {

        Request request = requestService.findRequest(acknowledgeRequestReceiptDTO.getRequestId());
        Status status = statusService.findOneStatus(acknowledgeRequestReceiptDTO.getStatusId());

        request.setStatus(status);
        requestRepository.save(request);

        AcknowledgeRequestReceipt acknowledgeRequestReceipt = AcknowledgeRequestReceipt
                .builder()
                .request(request)
                .user(currentUserService.getCurrentUser())
                .comment(acknowledgeRequestReceiptDTO.getComment())
                .build();

        AcknowledgeRequestReceipt response = acknowledgeRequestReceiptRepository.save(acknowledgeRequestReceipt);

        // Notify the Requester
        notifyIndividual(
                request.getRequester().getEmail(),
                acknowledgeRequestReceipt.getComment(),
                request,
                request.getRequester().getFirstName()
        );

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
}
