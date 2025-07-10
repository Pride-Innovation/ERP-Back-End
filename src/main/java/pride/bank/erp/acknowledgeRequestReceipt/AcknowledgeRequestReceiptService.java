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
import pride.bank.erp.request.Request;
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

    @Transactional
    public AcknowledgeRequestReceipt createAcknowledgeRequestReceipt(
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

        return acknowledgeRequestReceiptRepository.save(acknowledgeRequestReceipt);
    }

    public AcknowledgeRequestReceipt findAcknowledgeRequestReceiptByRequestId(Long requestId) {
        return acknowledgeRequestReceiptRepository.findByRequestId(requestId).orElse(null);
    }
}
