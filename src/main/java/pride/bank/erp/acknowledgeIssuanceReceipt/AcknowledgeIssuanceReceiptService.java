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
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.request.RequestService;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;

@Service
@RequiredArgsConstructor
public class AcknowledgeIssuanceReceiptService {
    private final AcknowledgeIssuanceReceiptRepository acknowledgeIssuanceReceiptRepository;
    private final RequestService requestService;
    private final StatusService statusService;
    private final RequestRepository requestRepository;
    private final CurrentUserService currentUserService;


    @Transactional
    public Request createAcknowledgeRequestIssuance(AcknowledgeIssuanceReceiptDTO acknowledgeIssuanceReceiptDTO)
            throws NotFoundException {
        Request request = requestService.findRequest(acknowledgeIssuanceReceiptDTO.getRequestId());
        Status status = statusService.findOneStatus(acknowledgeIssuanceReceiptDTO.getStatusId());

        request.setStatus(status);

        AcknowledgeIssuanceReceipt acknowledgeIssuanceReceipt = AcknowledgeIssuanceReceipt
                .builder()
                .comment(acknowledgeIssuanceReceiptDTO.getComment())
                .request(request)
                .user(currentUserService.getCurrentUser())
                .build();
        acknowledgeIssuanceReceiptRepository.save(acknowledgeIssuanceReceipt);
        return requestRepository.save(request);
    }

    public AcknowledgeIssuanceReceipt findAcknowledgeIssuanceReceiptByRequestId(Long requestId) {
        return acknowledgeIssuanceReceiptRepository.findByRequestId(requestId).orElse(null);
    }
}
