package pride.bank.erp.acknowledgeRequestReceipt;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestService;

@Service
@RequiredArgsConstructor
public class AcknowledgeRequestReceiptService {
    private final AcknowledgeRequestReceiptRepository acknowledgeRequestReceiptRepository;
    private final RequestService requestService;

    public AcknowledgeRequestReceipt createAcknowledgeRequestReceipt(
            AcknowledgeRequestReceiptDTO acknowledgeRequestReceiptDTO
    )
            throws NotFoundException {
        Request request = requestService.findRequest(acknowledgeRequestReceiptDTO.getRequestId());
        AcknowledgeRequestReceipt acknowledgeRequestReceipt = AcknowledgeRequestReceipt
                .builder()
                .request(request)
                .comment(acknowledgeRequestReceiptDTO.getComment())
                .build();

        return acknowledgeRequestReceiptRepository.save(acknowledgeRequestReceipt);
    }
}
