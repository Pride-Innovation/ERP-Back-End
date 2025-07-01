package pride.bank.erp.stockCommodity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class StockCommodityService {
    private final StockCommodityRepository stockCommodityRepository;

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
     * ensuring each asset type is present in every month with a value (defaulting to 0 if absent).
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

        for (Map<String, Object> monthlyData : grouped.values()) {
            for (String assetType : ASSET_TYPE_KEYS) {
                monthlyData.putIfAbsent(assetType, 0);
            }
        }

        return new ArrayList<>(grouped.values());
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
}
