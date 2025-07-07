package pride.bank.erp.acknowledgeRequestReceipt;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class AcknowledgeRequestReceiptController {
    private final AcknowledgeRequestReceiptService acknowledgeRequestReceiptService;

    @PostMapping("/acknowledge-request")
    public ResponseEntity<AcknowledgeRequestReceipt> createAcknowledgeRequestReceipt(
            AcknowledgeRequestReceiptDTO acknowledgeRequestReceiptDTO
    ) throws NotFoundException {
        AcknowledgeRequestReceipt response = acknowledgeRequestReceiptService
                .createAcknowledgeRequestReceipt(acknowledgeRequestReceiptDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
