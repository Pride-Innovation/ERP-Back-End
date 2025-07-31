package pride.bank.erp.requestCommodity;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.helper.Constants;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class RequestCommodityController {

    private final RequestCommodityService requestCommodityService;

    @GetMapping("/request-rating-stats")
    public ResponseEntity<Map<String, Integer>> getMonthlyCommodityStats() {
        return ResponseEntity.ok(requestCommodityService.getMonthlyCommodityStats());
    }

    @GetMapping("/request-stats-one-year")
    public ResponseEntity<List<MonthlyCommoditySummary>> getMonthlyCommodityStatsLastYear() {
        return ResponseEntity.ok(requestCommodityService.getMonthlyCommodityStatsLastYearForBranch());
    }

    @GetMapping("/monthly-itAndOffice-stats")
    public ResponseEntity<List<MonthlyAssetTotals>> getMonthlyItAndOfficeStats() {
        return ResponseEntity.ok(requestCommodityService.getMonthlyItAndOfficeStats());
    }

    @GetMapping("/stationery/monthly-request")
    public ResponseEntity<MonthlyStationeryStockDTO> getStationeryRequestsForCurrentMonth() {
        return ResponseEntity.ok(requestCommodityService.getCurrentMonthStationeryRequests());
    }
}
