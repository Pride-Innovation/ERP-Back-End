/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.issuanceApprovalRecord;

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

    @GetMapping("/approve-issuance/{requestId}")
    public ResponseEntity<IssuanceApprovalRecord> findIssuanceApprovalRecordByRequestId(
            @PathVariable Long requestId
    ) throws NotFoundException {
        return new ResponseEntity<>(
                issuanceApprovalRecordService.findIssuanceApprovalRecordByRequestId(requestId),
                HttpStatus.OK
        );
    }
}
