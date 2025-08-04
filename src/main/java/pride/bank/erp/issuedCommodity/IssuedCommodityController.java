package pride.bank.erp.issuedCommodity;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.helper.Constants;

import java.util.List;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class IssuedCommodityController {
    private final IssuedCommodityService issuedCommodityService;

    @GetMapping("/it-and-office/monthly-stock/summary")
    public ResponseEntity<List<AssetTypeCommodityStats>> printTopAndLeastIssued() {
        return new ResponseEntity<>(issuedCommodityService.printTopAndLeastIssued(), HttpStatus.OK);
    }
}
