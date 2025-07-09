package pride.bank.erp.acknowledgeIssuanceReceipt;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.request.Request;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class AcknowledgeIssuanceReceiptController {
    private final AcknowledgeIssuanceReceiptService acknowledgeIssuanceReceiptService;

    @PostMapping("/acknowledge-issuance")
    public ResponseEntity<Request> createAcknowledgeRequestIssuance(
            @RequestBody AcknowledgeIssuanceReceiptDTO acknowledgeIssuanceReceiptDTO
    ) throws NotFoundException {
        Request response = acknowledgeIssuanceReceiptService
                .createAcknowledgeRequestIssuance(acknowledgeIssuanceReceiptDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/acknowledge-issuance/{requestId}")
    public ResponseEntity<AcknowledgeIssuanceReceipt> findAcknowledgeIssuanceReceiptByRequestId(
            @PathVariable Long requestId
    ) throws NotFoundException {
        return new ResponseEntity<>(
                acknowledgeIssuanceReceiptService.findAcknowledgeIssuanceReceiptByRequestId(requestId),
                HttpStatus.OK
        );
    }
}
