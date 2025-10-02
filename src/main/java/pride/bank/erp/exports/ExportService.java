/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/
package pride.bank.erp.exports;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetDAO;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.status.Status;
import pride.bank.erp.stock.CommodityWithDetailsDao;
import pride.bank.erp.stock.Stock;
import pride.bank.erp.stock.StockDAO;
import pride.bank.erp.stock.StockRepository;
import pride.bank.erp.stockCommodity.StockCommodity;
import pride.bank.erp.stockCommodity.StockCommodityRepository;
import pride.bank.erp.supplier.Supplier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {
    private final StockRepository stockRepository;
    private final StockCommodityRepository stockCommodityRepository;
    private final AssetRepository assetRepository;
    private final AssetService assetService;

    public List<StockDAO> fetchAllStocks(
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        List<Stock> stocks;

        if (startDate != null && endDate != null) {
            stocks = stockRepository.findByCreateDateBetween(startDate, endDate);
        } else {
            stocks = stockRepository.findAll();
        }

        if (stocks.isEmpty()) return List.of();

        List<Long> stockIds = stocks.stream().map(Stock::getId).toList();
        List<StockCommodity> allStockCommodities = stockCommodityRepository.findByStockIdIn(stockIds);

        Map<Long, List<StockCommodity>> commoditiesByStockId = allStockCommodities.stream()
                .collect(Collectors.groupingBy(sc -> sc.getStock().getId()));

        stocks.forEach(stock -> {
            List<StockCommodity> scList = commoditiesByStockId.getOrDefault(stock.getId(), List.of());
            List<CommodityWithDetailsDao> details = scList.stream()
                    .map(sc -> CommodityWithDetailsDao.builder()
                            .commodity(sc.getCommodity())
                            .orderedQuantity(sc.getOrderedQuantity())
                            .deliveredQuantity(sc.getDeliveredQuantity())
                            .costPrice(sc.getCostPrice())
                            .purchasePrice(sc.getPurchasePrice())
                            .build())
                    .toList();

            stock.setCommodities(details);
        });

        return stocks.stream().map(this::mapToStockDAO).toList();
    }

    private StockDAO mapToStockDAO(Stock stock) {
        return StockDAO.builder()
                .id(stock.getId())
                .referenceNumber(stock.getReferenceNumber())
                .name(stock.getName())
                .totalCost(stock.getTotalCost())
                .balanceCost(stock.getBalanceCost())
                .grnNumber(stock.getGrnNumber())
                .lpoNumber(stock.getLpoNumber())
                .status(stock.getStatus() != null ?
                        Status.builder()
                                .name(stock.getStatus().getName())
                                .description(stock.getStatus().getDescription())
                                .id(stock.getStatus().getId())
                                .status(stock.getStatus().getStatus())
                                .build() : null)
                .branch(stock.getBranch() != null ?
                        Branch.builder()
                                .id(stock.getBranch().getId())
                                .email(stock.getBranch().getEmail())
                                .telephone(stock.getBranch().getTelephone())
                                .name(stock.getBranch().getName())
                                .build() : null)
                .supplier(stock.getSupplier() != null ?
                        Supplier.builder()
                                .name(stock.getSupplier().getName())
                                .address(stock.getSupplier().getAddress())
                                .telephone(stock.getSupplier().getTelephone())
                                .email(stock.getSupplier().getEmail())
                                .build() : null)
                .createDate(stock.getCreateDate())
                .lastModified(stock.getLastModified())
                .createdBy(stock.getCreatedBy())
                .lastModifiedBy(stock.getLastModifiedBy())
                .commodities(stock.getCommodities())
                .grnReports(stock.getGrnReports())
                .canNotBeDeleted(stock.isCanNotBeDeleted())
                .build();
    }

    public List<AssetDAO> fetchAllAssets(Long assetTypeId) {
        List<Asset> response = assetRepository.findByAssetTypeId(assetTypeId);
        return mapToDTOList(response);
    }

    private List<AssetDAO> mapToDTOList(List<Asset> assets) {
        return assets.stream().map(assetService::buildAssetDao).toList();
    }

}
