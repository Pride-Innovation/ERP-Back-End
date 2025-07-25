package pride.bank.erp.acknowledgeRequestReceipt;

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
public class AcknowledgeRequestReceiptController {
    private final AcknowledgeRequestReceiptService acknowledgeRequestReceiptService;

    @PostMapping("/acknowledge-request")
    public ResponseEntity<Request> createAcknowledgeRequestReceipt(
            @RequestBody AcknowledgeRequestReceiptDTO acknowledgeRequestReceiptDTO
    ) throws NotFoundException {
        Request response = acknowledgeRequestReceiptService
                .createAcknowledgeRequestReceipt(acknowledgeRequestReceiptDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/acknowledge-request/{requestId}")
    public ResponseEntity<AcknowledgeRequestReceipt> findAcknowledgeRequestReceiptByRequestId(
            @PathVariable Long requestId
    ) throws NotFoundException {
        return new ResponseEntity<>(
                acknowledgeRequestReceiptService.findAcknowledgeRequestReceiptByRequestId(requestId),
                HttpStatus.OK
        );
    }

}
