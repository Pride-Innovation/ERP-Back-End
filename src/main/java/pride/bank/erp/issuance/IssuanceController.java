/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.issuance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.issuedCommodity.IssuedCommodity;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class IssuanceController {
    private final IssuanceService issuanceService;

    @PostMapping("/issuance")
    public ResponseEntity<IssuanceDAO> issueRequest(@RequestBody IssuanceDTO issuanceDTO)
            throws NotFoundException {
        IssuanceDAO response = issuanceService.issueCommodities(issuanceDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /*
     * The two reads below were open to any signed-in user.
     *
     * REQUEST_ISSUANCE (/issuance/**) is matched for POST only, so the GET fell through to
     * `.anyRequest().authenticated()`; /last-issued/** is not under that prefix at all. A
     * method-level gate is used rather than another matcher so the rule sits beside the code it
     * governs and cannot drift from it.
     */
    @PreAuthorize("hasAnyAuthority('" + Constants.READ_REQUEST + "', '" + Constants.ISSUE_ITEMS + "')")
    @GetMapping("/last-issued/{commodityId}")
    public ResponseEntity<IssuedCommodity> getLastIssuedCommodity(@PathVariable Long commodityId) {
        IssuedCommodity response = issuanceService.getLastIssuedCommodity(commodityId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * What was issued against one request.
     *
     * <h2>Gated, but deliberately not scoped to the requester's branch</h2>
     * Issuance is inherently cross-branch: the ordinary case is a Head Office storekeeper fulfilling
     * a branch's request, which is the same reason {@code createMovement} carries no scope check.
     * Narrowing this by the requester's branch would refuse the issuer their own work — and it would
     * do so only <em>sometimes</em>, because {@code WorkflowEngineService} nulls
     * {@code currentApprover} once every step is complete, so the "routed to me" exception that
     * carries an approver through has already lapsed by the time anything is issued.
     *
     * <p>{@code ISSUE_ITEMS} is therefore the control here, and it is a narrow permission held by the
     * people who do this job. The request record itself stays scoped; this is the issuance against
     * it.
     */
    @PreAuthorize("hasAnyAuthority('" + Constants.READ_REQUEST + "', '" + Constants.ISSUE_ITEMS + "')")
    @GetMapping("/issuance/{requestId}")
    public ResponseEntity<IssuanceDAO> findByRequestId(@PathVariable Long requestId)
            throws NotFoundException {
        IssuanceDAO response = issuanceService.findByRequestId(requestId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}