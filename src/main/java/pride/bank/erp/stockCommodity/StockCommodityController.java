package pride.bank.erp.stockCommodity;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.requestCommodity.MonthlyStationeryStockDTO;

import java.util.List;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class StockCommodityController {

    private final StockCommodityService stockCommodityService;

    @GetMapping("/monthly-asset-stock")
    public ResponseEntity<?> getMonthlyAssetStockSummary() {
        return ResponseEntity.ok(stockCommodityService.getStockSummaryLastSixMonths());
    }


    @GetMapping("/stationery/monthly-stock")
    public ResponseEntity<MonthlyStationeryStockDTO> getStationeryRequestsForCurrentMonth() {
        return ResponseEntity.ok(stockCommodityService.getCurrentMonthStationeryStocks());
    }

    @GetMapping("/it-and-office/monthly-stock")
    public ResponseEntity<List<StockSummaryDTO>> getItAndOfficeMonthlyStockSummary() {
        return ResponseEntity.ok(stockCommodityService.getItAndOfficeMonthlyStockSummary());
    }
}
