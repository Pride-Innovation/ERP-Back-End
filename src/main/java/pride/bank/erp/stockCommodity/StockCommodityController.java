package pride.bank.erp.stockCommodity;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.helper.Constants;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class StockCommodityController {

    private final StockCommodityService stockCommodityService;

    @GetMapping("/monthly-asset-stock")
    public ResponseEntity<?> getMonthlyAssetStockSummary() {
        return ResponseEntity.ok(stockCommodityService.getStockSummaryLastSixMonths());
    }
}
