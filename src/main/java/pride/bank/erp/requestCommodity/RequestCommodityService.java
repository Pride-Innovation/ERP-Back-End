package pride.bank.erp.requestCommodity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestCommodityService {
    private final RequestCommodityRepository requestCommodityRepository;

    /**
     * Returns the total quantity of commodities requested in the current and previous months.
     * It calculates the totals using the request's creation date, grouped by month and year.
     */
    public Map<String, Integer> getMonthlyCommodityStats() {
        LocalDate now = LocalDate.now();

        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        LocalDate previousMonthDate = now.minusMonths(1);
        int previousMonth = previousMonthDate.getMonthValue();
        int previousYear = previousMonthDate.getYear();

        Integer currentMonthTotal = requestCommodityRepository.getTotalQuantityByMonthAndYear(currentMonth, currentYear);
        Integer previousMonthTotal = requestCommodityRepository.getTotalQuantityByMonthAndYear(previousMonth, previousYear);

        Map<String, Integer> result = new HashMap<>();
        result.put("currentMonth", currentMonthTotal != null ? currentMonthTotal : 0);
        result.put("previousMonth", previousMonthTotal != null ? previousMonthTotal : 0);

        return result;
    }

    public List<MonthlyCommoditySummary> getMonthlyCommodityStatsLastYear() {
        List<Object[]> results = requestCommodityRepository.getMonthlyCommodityQuantitiesLastYear();

        return results.stream()
                .map(row -> new MonthlyCommoditySummary(
                        (String) row[1],
                        ((Number) row[2]).intValue()
                ))
                .toList();
    }

    public List<MonthlyAssetTotals> getMonthlyItAndOfficeStats() {
        return requestCommodityRepository.fetchMonthlyItAndOfficeTotals().stream()
                .map(row -> new MonthlyAssetTotals(
                        (String) row[1],
                        ((Number) row[2]).intValue(),
                        ((Number) row[3]).intValue()
                ))
                .toList();
    }
}
