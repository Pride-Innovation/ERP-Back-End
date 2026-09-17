/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock.reconciliation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.grnCommodity.GrnCommodityRepository;
import pride.bank.erp.stock.Stock;
import pride.bank.erp.stockCommodity.StockCommodity;
import pride.bank.erp.stockCommodity.StockCommodityRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-checks the three records of a received unit that are supposed to agree: the order line's
 * delivered quantity, the GRN trail behind it, and the asset register.
 *
 * <p>They can disagree because, until receipts became the only way in, the correction form could
 * raise a line's delivered quantity on its own — no GRN, no store credit, no assets. The screen
 * then showed goods that the rest of the system had never heard of. This report finds those lines.
 *
 * <p>It is deliberately <b>read-only</b>. It reports; it does not repair. What to do about a
 * discrepancy — write off, receive properly, or correct the order — is a business decision, and
 * silently generating stock movements to paper over it would be exactly the kind of invisible
 * adjustment that caused the problem in the first place.
 */
@Service
@RequiredArgsConstructor
public class StockReconciliationService {

    private final StockCommodityRepository stockCommodityRepository;
    private final GrnCommodityRepository grnCommodityRepository;
    private final AssetRepository assetRepository;

    @Transactional(readOnly = true)
    public StockReconciliationReport buildReport(boolean onlyDiscrepancies) {
        Map<String, Integer> receivedByLine = toLookup(grnCommodityRepository.sumReceivedQuantityByStockAndCommodity());
        Map<String, Integer> assetsByLine = toLookup(assetRepository.countAssetsByStockAndCommodity());

        List<StockCommodityRow> examined = new ArrayList<>();
        for (StockCommodity line : stockCommodityRepository.findAllForActiveStocks()) {
            examined.add(new StockCommodityRow(line,
                    receivedByLine.getOrDefault(key(line.getStock().getId(), line.getCommodity().getId()), 0),
                    assetsByLine.getOrDefault(key(line.getStock().getId(), line.getCommodity().getId()), 0)));
        }

        List<StockReconciliationRow> rows = new ArrayList<>();
        int discrepantCount = 0;
        int totalMissingFromGrn = 0;
        int totalAssetsMissing = 0;

        for (StockCommodityRow row : examined) {
            StockReconciliationRow view = row.toView();
            if (view.isDiscrepant()) {
                discrepantCount++;
                totalMissingFromGrn += view.getQuantityMissingFromGrn();
                totalAssetsMissing += view.getAssetsMissing();
            }
            if (!onlyDiscrepancies || view.isDiscrepant()) {
                rows.add(view);
            }
        }

        return StockReconciliationReport.builder()
                .linesExamined(examined.size())
                .linesWithDiscrepancies(discrepantCount)
                .totalQuantityMissingFromGrn(totalMissingFromGrn)
                .totalAssetsMissing(totalAssetsMissing)
                .rows(rows)
                .build();
    }

    /** Turns {@code [stockId, commodityId, number]} tuples into a lookup keyed by the pair. */
    private Map<String, Integer> toLookup(List<Object[]> tuples) {
        Map<String, Integer> lookup = new HashMap<>();
        for (Object[] tuple : tuples) {
            if (tuple[0] == null || tuple[1] == null) continue;
            Long stockId = ((Number) tuple[0]).longValue();
            Long commodityId = ((Number) tuple[1]).longValue();
            int value = tuple[2] == null ? 0 : ((Number) tuple[2]).intValue();
            lookup.merge(key(stockId, commodityId), value, Integer::sum);
        }
        return lookup;
    }

    private static String key(Long stockId, Long commodityId) {
        return stockId + ":" + commodityId;
    }

    /** One order line together with the two independent counts it is checked against. */
    private record StockCommodityRow(StockCommodity line, int grnCovered, int assetsOnRegister) {

        StockReconciliationRow toView() {
            Stock stock = line.getStock();
            Commodity commodity = line.getCommodity();
            AssetType assetType = commodity.getAssetType();
            boolean tracksAssets = assetType != null && assetType.isTracksAssets();

            int delivered = line.getDeliveredQuantity();
            int missingFromGrn = Math.max(delivered - grnCovered, 0);
            int assetsMissing = tracksAssets ? Math.max(delivered - assetsOnRegister, 0) : 0;

            // A GRN trail that exceeds the delivered figure is just as wrong as one that falls
            // short — it means the line was corrected downwards after the goods were received.
            boolean discrepant = missingFromGrn > 0
                    || grnCovered > delivered
                    || assetsMissing > 0
                    || (tracksAssets && line.getRegisteredNumber() != assetsOnRegister);

            return StockReconciliationRow.builder()
                    .stockId(stock.getId())
                    .lpoNumber(stock.getLpoNumber())
                    .stockName(stock.getName())
                    .branchName(stock.getBranch() != null ? stock.getBranch().getName() : null)
                    .statusName(stock.getStatus() != null ? stock.getStatus().getName() : null)
                    .commodityId(commodity.getId())
                    .commodityName(commodity.getName())
                    .assetTypeName(assetType != null ? assetType.getName() : null)
                    .tracksAssets(tracksAssets)
                    .orderedQuantity(line.getOrderedQuantity())
                    .deliveredQuantity(delivered)
                    .grnCoveredQuantity(grnCovered)
                    .registeredNumber(line.getRegisteredNumber())
                    .assetsOnRegister(assetsOnRegister)
                    .quantityMissingFromGrn(missingFromGrn)
                    .assetsMissing(assetsMissing)
                    .discrepant(discrepant)
                    .build();
        }
    }
}
