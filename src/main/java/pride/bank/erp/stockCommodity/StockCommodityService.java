package pride.bank.erp.stockCommodity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.requestCommodity.MonthlyStationeryStockDTO;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockCommodityService {
    private final StockCommodityRepository stockCommodityRepository;
    private final CurrentUserService currentUserService;
    private final pride.bank.erp.dashboard.DashboardScopeService dashboardScopeService;

    /*
     * There is deliberately no hardcoded category list here any more.
     *
     * There used to be two: a four-entry key list that padded every month, and a four-entry name
     * map used to translate a category name into a key. Three of those four categories no longer
     * exist — the seed now defines twelve, with Computers, Furniture and Vehicle/Fleet among them —
     * so every real category fell through the map to "unknown" and collapsed into one bucket, while
     * the response advertised four obsolete names with zeros against them. The dashboard rendered
     * exactly that: a filter listing categories the bank no longer has.
     *
     * Categories are configuration, not code. Both the keys and the padding are now derived from
     * what the query actually returns, so adding a category in Settings is the only step needed for
     * it to appear here.
     */

    /**
     * Returns a list of monthly stock summaries for the last 6 months,
     * including months with no data (with zeros for all asset types).
     */
    public List<Map<String, Object>> getStockSummaryLastSixMonths(Long requestedBranchId) {
        Long branchId = dashboardScopeService.resolveBranch(requestedBranchId);
        List<MonthlyStockSummaryDTO> raw =
                stockCommodityRepository.getMonthlyStockSummaryForLastSixMonthsScoped(branchId);

        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();

        for (MonthlyStockSummaryDTO entry : raw) {
            String month = entry.getMonth();
            String assetTypeKey = formatAssetTypeKey(entry.getAssetType());

            grouped.putIfAbsent(month, new LinkedHashMap<>());
            Map<String, Object> monthlyData = grouped.get(month);

            monthlyData.put("month", month);
            monthlyData.put(assetTypeKey, entry.getTotalQuantity());
        }

        List<String> lastSixMonths = getLastSixMonths();

        // Pad from the categories actually present across the window, so every month carries the
        // same keys and a month with no stocking reads as zero rather than as a missing series.
        Set<String> presentKeys = new LinkedHashSet<>();
        for (Map<String, Object> monthlyData : grouped.values()) {
            for (String key : monthlyData.keySet()) {
                if (!"month".equals(key)) presentKeys.add(key);
            }
        }

        for (String month : lastSixMonths) {
            grouped.putIfAbsent(month, new LinkedHashMap<>());
            Map<String, Object> monthlyData = grouped.get(month);

            monthlyData.put("month", month);

            for (String assetType : presentKeys) {
                monthlyData.putIfAbsent(assetType, 0);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String month : lastSixMonths) {
            result.add(grouped.get(month));
        }

        return result;
    }

    /**
     * Generates a list of the last six months formatted as "yyyy-MM"
     */
    private List<String> getLastSixMonths() {
        List<String> months = new ArrayList<>();
        LocalDate now = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (int i = 5; i >= 0; i--) {
            months.add(now.minusMonths(i).format(formatter));
        }

        return months;
    }

    /**
     * Normalizes asset type names from the database and maps them to their expected camelCase keys.
     * Returns "unknown" if the asset type name is null or unrecognized.
     */
    private String formatAssetTypeKey(String name) {
        if (name == null || name.isBlank()) return "unknown";

        // Camel-cases whatever the category is actually called: "IT Equipment" becomes itEquipment,
        // "Vehicle/Fleet" becomes vehicleFleet. The browser normalises both sides by stripping
        // non-alphanumerics and lower-casing, so this only has to be stable — not to match a list.
        String[] words = name.trim().split("[^A-Za-z0-9]+");
        StringBuilder key = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (key.isEmpty()) {
                key.append(word.toLowerCase());
            } else {
                key.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
            }
        }
        return key.isEmpty() ? "unknown" : key.toString();
    }


    /**
     * @return Current Month Stationery Stock Report
     */
    public MonthlyStationeryStockDTO getCurrentMonthStationeryStocks() {
        Long branchId = currentUserService.getCurrentUser().getBranch().getId();

        List<Object[]> rows = stockCommodityRepository.findCurrentMonthStationeryStockSums(branchId);

        Map<String, Integer> commodityCounts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            commodityCounts.put((String) row[0], ((Number) row[1]).intValue());
        }

        String month = LocalDate.now()
                .getMonth()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        return MonthlyStationeryStockDTO.builder()
                .month(month)
                .commodities(commodityCounts)
                .build();
    }


    public List<StockSummaryDTO> getItAndOfficeMonthlyStockSummary() {
        List<StockSummaryDTO> summaryList = new ArrayList<>();
        Long branchId = currentUserService.getCurrentUser().getBranch().getId();

        List<Object[]> itData = stockCommodityRepository
                .findTopAndLeastITStockedCommodities(branchId);
        if (itData.size() == 2) {
            summaryList.add(new StockSummaryDTO(
                    "IT Equipment",
                    (String) itData.get(0)[0],
                    ((Number) itData.get(0)[1]).longValue(),
                    (String) itData.get(1)[0],
                    ((Number) itData.get(1)[1]).longValue()
            ));
        }

        List<Object[]> officeData = stockCommodityRepository.findTopAndLeastOfficeStockedCommodities();
        if (officeData.size() == 2) {
            summaryList.add(new StockSummaryDTO(
                    "Office Equipment",
                    (String) officeData.get(0)[0],
                    ((Number) officeData.get(0)[1]).longValue(),
                    (String) officeData.get(1)[0],
                    ((Number) officeData.get(1)[1]).longValue()
            ));
        }

        return summaryList;
    }

    public List<AnnualStockSummaryDTO> getAnnualStockSummary() {
        List<MonthlyStockSummary> rawResults = stockCommodityRepository.getMonthlyStockSummary();

        return rawResults.stream()
                .map(row -> new AnnualStockSummaryDTO(
                        row.getMonth().trim(),
                        row.getItEquipment(),
                        row.getOfficeEquipment()
                ))
                .collect(Collectors.toList());
    }

}
