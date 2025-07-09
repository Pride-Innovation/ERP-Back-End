package pride.bank.erp.issuanceApprovalRecord;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.request.Request;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class IssuanceApprovalRecordController {
    private final IssuanceApprovalRecordService issuanceApprovalRecordService;

    @PostMapping("/approve-issuance")
    public ResponseEntity<Request> createIssuanceApprovalRecord(
            @RequestBody IssuanceApprovalRecordDTO request
    ) throws NotFoundException {
        Request response = issuanceApprovalRecordService
                .createIssuanceApprovalRecord(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
