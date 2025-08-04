package pride.bank.erp.issuedCommodity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pride.bank.erp.config.CurrentUserService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssuedCommodityService {

    private final IssuedCommodityRepository issuedCommodityRepository;
    private final CurrentUserService currentUserService;

    public List<AssetTypeCommodityStats> printTopAndLeastIssued() {
        Long branchId = currentUserService.getCurrentUser().getBranch().getId();
        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        List<CommoditySummary> rawStats =
                issuedCommodityRepository.findAllCommodityQuantitiesGroupedByAssetType(branchId, currentMonth, currentYear);

        Map<String, List<CommoditySummary>> groupedByAssetType = rawStats.stream()
                .collect(Collectors.groupingBy(CommoditySummary::getAssetType));

        List<AssetTypeCommodityStats> results = new ArrayList<>();

        for (Map.Entry<String, List<CommoditySummary>> entry : groupedByAssetType.entrySet()) {
            String assetType = entry.getKey();
            List<CommoditySummary> commodities = entry.getValue();

            CommoditySummary mostIssued = commodities.stream()
                    .max(Comparator.comparingLong(CommoditySummary::getTotalQuantity))
                    .orElse(null);

            CommoditySummary leastIssued = commodities.stream()
                    .min(Comparator.comparingLong(CommoditySummary::getTotalQuantity))
                    .orElse(null);

            if (mostIssued != null && leastIssued != null) {
                AssetTypeCommodityStats stats = AssetTypeCommodityStats
                        .builder()
                        .assetType(assetType)
                        .mostStockedItem(mostIssued.getCommodityName())
                        .mostStockedQuantity(mostIssued.getTotalQuantity())
                        .leastStockedItem(leastIssued.getCommodityName())
                        .leastStockedQuantity(leastIssued.getTotalQuantity())
                        .build();

                results.add(stats);
            }
        }

        return results;
    }
}
