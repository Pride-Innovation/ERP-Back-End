package pride.bank.erp.issuance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

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
}
