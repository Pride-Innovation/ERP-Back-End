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

    /**
     * Six months of stocking volume, scoped to the viewer.
     *
     * @param branchId the branch the dashboard's selector is focused on; only meaningful at ALL
     *                 scope. This endpoint previously had no branch predicate whatsoever, so every
     *                 user in the bank saw the same national figure under a widget labelled with
     *                 their own branch.
     */
    @GetMapping("/monthly-asset-stock")
    public ResponseEntity<?> getMonthlyAssetStockSummary(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(stockCommodityService.getStockSummaryLastSixMonths(branchId));
    }


    @GetMapping("/stationery/monthly-stock")
    public ResponseEntity<MonthlyStationeryStockDTO> getStationeryRequestsForCurrentMonth() {
        return ResponseEntity.ok(stockCommodityService.getCurrentMonthStationeryStocks());
    }

    @GetMapping("/it-and-office/monthly-stock")
    public ResponseEntity<List<StockSummaryDTO>> getItAndOfficeMonthlyStockSummary() {
        return ResponseEntity.ok(stockCommodityService.getItAndOfficeMonthlyStockSummary());
    }


    @GetMapping("/it-and-office/annual-stock")
    public ResponseEntity<List<AnnualStockSummaryDTO>> getAnnualStockSummary() {
        return ResponseEntity.ok(stockCommodityService.getAnnualStockSummary());
    }
}
