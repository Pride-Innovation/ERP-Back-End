package pride.bank.erp.stockCommodity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.requestCommodity.MonthlyStationeryStockDTO;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StockCommodityService {
    private final StockCommodityRepository stockCommodityRepository;
    private final CurrentUserService currentUserService;

    private static final List<String> ASSET_TYPE_KEYS = Arrays.asList(
            "itEquipment", "officeEquipment", "stationery", "fleet"
    );

    private static final Map<String, String> ASSET_TYPE_NAME_MAP = Map.of(
            "it equipment", "itEquipment",
            "office equipment", "officeEquipment",
            "stationery", "stationery",
            "fleet", "fleet"
    );

    /**
     * Returns a list of monthly stock summaries for the last 6 months,
     * including months with no data (with zeros for all asset types).
     */
    public List<Map<String, Object>> getStockSummaryLastSixMonths() {
        List<MonthlyStockSummaryDTO> raw = stockCommodityRepository.getMonthlyStockSummaryForLastSixMonths();

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

        for (String month : lastSixMonths) {
            grouped.putIfAbsent(month, new LinkedHashMap<>());
            Map<String, Object> monthlyData = grouped.get(month);

            monthlyData.put("month", month);

            for (String assetType : ASSET_TYPE_KEYS) {
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
        if (name == null) return "unknown";
        String normalized = name.trim().toLowerCase();
        return ASSET_TYPE_NAME_MAP.getOrDefault(normalized, "unknown");
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
}
