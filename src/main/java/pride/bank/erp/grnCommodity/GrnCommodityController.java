package pride.bank.erp.grnCommodity;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.helper.Constants;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class GrnCommodityController {
    private final GrnCommodityService grnCommodityService;

    @GetMapping("/grn-commodities/{stockID}")
    public ResponseEntity<Page<GrnCommodity>> fetchAllGrnCommodities(
            @PathVariable Long stockID,
            Pageable pageable
    ) {
        Page<GrnCommodity> result = grnCommodityService.fetchAllGrnCommodities(stockID, pageable);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
