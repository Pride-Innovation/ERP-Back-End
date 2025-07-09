package pride.bank.erp.issuance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Issuance> issueRequest(@RequestBody IssuanceDTO issuanceDTO)
            throws NotFoundException {
        Issuance response = issuanceService.issueCommodities(issuanceDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/last-issued/{commodityId}")
    public ResponseEntity<IssuedCommodity> getLastIssuedCommodity(@PathVariable Long commodityId) {
        IssuedCommodity response = issuanceService.getLastIssuedCommodity(commodityId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/issuance/{requestId}")
    public ResponseEntity<Issuance> findByRequestId(@PathVariable Long requestId)
            throws NotFoundException {
        Issuance response = issuanceService.findByRequestId(requestId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}