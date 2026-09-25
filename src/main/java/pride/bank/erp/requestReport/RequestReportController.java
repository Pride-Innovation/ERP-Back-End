/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.requestReport;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import pride.bank.erp.advice.AccessDenied;
import pride.bank.erp.advice.WafStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.BusinessRuleException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.request.RequestDTO;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;


@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
public class RequestReportController {
    private final RequestReportService requestReportService;
    private final pride.bank.erp.request.RequestService requestService;

    @PostMapping("/approvals")
    public ResponseEntity<?> approveRequest(@RequestBody RequestReportDTO requestReportDTO) {
        try {
            RequestDTO response = requestReportService.approveRequest(requestReportDTO);
            return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
        } catch (BusinessRuleException e) {
            return ResponseEntity.status(AccessDenied.STATUS).body(e.getMessage());
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(WafStatus.SERVER_FAULT).body(e.getMessage());
        }
    }

    /**
     * A request's approval report.
     *
     * <p>Sat outside {@code /requests/**}, so the route matchers that guard the module did not cover
     * it and any signed-in user could read it by id. Gated and scoped through the request it belongs
     * to, like the approval trail.
     */
    @PreAuthorize("hasAuthority('" + pride.bank.erp.helper.Constants.READ_REQUEST + "')")
    @GetMapping("/request-report/{requestId}")
    public ResponseEntity<RequestReportDAO> findRequestReportByRequestId(@PathVariable Long requestId)
            throws NotFoundException {
        requestService.requireReadableById(requestId);
        return ResponseEntity.ok(requestReportService.findRequestReportByRequestId(requestId));
    }
}
