/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.requestReport;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.request.Request;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;


@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
public class RequestReportController {
    private final RequestReportService requestReportService;

    @PostMapping("/approvals")
    public ResponseEntity<Request> approveRequest(@RequestBody RequestReportDTO requestReportDTO)
            throws NotFoundException {
        Request response = requestReportService.approveRequest(requestReportDTO);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }
}
