/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchService;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.stockCommodity.StockCommodity;
import pride.bank.erp.stockCommodity.StockCommodityDTO;
import pride.bank.erp.stockCommodity.StockCommodityRepository;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.supplier.SupplierService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@Service
@RequiredArgsConstructor
public class StockService {
    private final StockRepository stockRepository;
    private final StatusService statusService;
    private final SupplierService supplierService;
    private final CommodityService commodityService;
    private final StockCommodityRepository stockCommodityRepository;
    private final BranchService branchService;
    private final StoreService storeService;
    private final StockSearchDao stockSearchDao;
    private final AssetRepository assetRepository;

    @Transactional
    public Page<Stock> fetchAllStocks(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String lpoNumber
    ) {

        StockPage stockPage = StockPage.builder()
                .pageSize(pageSize)
                .sortDirection(determineSortDirection(sortDirection))
                .pageNumber(pageNumber)
                .sortBy(sortBy)
                .build();

        StockSearchCriteria criteria = StockSearchCriteria.builder()
                .lpoNumber(lpoNumber)
                .build();

        Page<Stock> stocks = stockSearchDao.findAllWithFilters(stockPage, criteria);

        List<Long> stockIds = stocks.stream()
                .map(Stock::getId)
                .toList();

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

        return stocks;
    }


    @Transactional
    public Stock createStock(
            Stock request,
            Long supplierID,
            List<StockCommodityDTO> stockCommoditiesRequest
    )
            throws NotFoundException {
        Status status = null;
        Supplier supplier = supplierService.findSupplier(supplierID);
        Summation summation = calculateSums(stockCommoditiesRequest);
        Branch headOffice = branchService.findBranch(1L);

        if (summation.getDeliveredCommoditiesSummation() == summation.getOrderedCommoditiesSummation()) {
            status = statusService.findOneStatus(4L);
        }

        if (summation.getDeliveredCommoditiesSummation() < summation.getOrderedCommoditiesSummation()) {
            status = statusService.findOneStatus(1L);
        }

        Stock stock = Stock
                .builder()
                .referenceNumber(request.getReferenceNumber())
                .totalCost(request.getTotalCost())
                .balanceCost(request.getBalanceCost())
                .name(request.getName())
                .branch(headOffice)
                .lpoNumber(request.getLpoNumber())
                .grnNumber(request.getGrnNumber())
                .status(status)
                .supplier(supplier)
                .build();

        Stock savedStock = stockRepository.save(stock);
        addCommoditiesToStock(savedStock, stockCommoditiesRequest);

        createAssetsFromStock(savedStock);

        List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(savedStock.getId());
        List<CommodityWithDetailsDao> commodities = commodityWithDetailsDao(stockCommodities);

        storeService.addCommoditiesToStore(commodities, 1L);
        savedStock.setCommodities(commodities);
        return savedStock;
    }

    public Stock updateStock(
            Long stockID,
            Stock request,
            Long statusID,
            Long supplierID,
            List<StockCommodityDTO> stockCommoditiesRequest
    ) throws NotFoundException {
        Stock stock = findOneStock(stockID);
        Status status = statusService.findOneStatus(statusID);
        Supplier supplier = supplierService.findSupplier(supplierID);

        stock.setReferenceNumber(request.getReferenceNumber());
        stock.setTotalCost(request.getTotalCost());
        stock.setBalanceCost(request.getBalanceCost());
        stock.setStatus(status);
        stock.setSupplier(supplier);

        Stock savedStock = stockRepository.save(stock);
        stockCommodityRepository.deleteByStockId(stockID);
        addCommoditiesToStock(savedStock, stockCommoditiesRequest);

        List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(savedStock.getId());
        List<CommodityWithDetailsDao> commodities = commodityWithDetailsDao(stockCommodities);
        savedStock.setCommodities(commodities);
        return savedStock;
    }

    public Stock findOneStock(Long id) throws NotFoundException {
        return stockRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("No stock with id %s is found", id))
        );
    }


    private Summation calculateSums(List<StockCommodityDTO> stockCommodities) {
        int orderedSum = 0;
        int deliveredSum = 0;
        for (StockCommodityDTO dto : stockCommodities) {
            orderedSum += dto.getOrderedQuantity();
            deliveredSum += dto.getDeliveredQuantity();
        }
        return Summation.builder()
                .deliveredCommoditiesSummation(deliveredSum)
                .orderedCommoditiesSummation(orderedSum)
                .build();
    }

    @Transactional
    private void addCommoditiesToStock(Stock stock, List<StockCommodityDTO> stockCommodities)
            throws NotFoundException {
        for (StockCommodityDTO dto : stockCommodities) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
            StockCommodity stockCommodity = StockCommodity
                    .builder()
                    .commodity(commodity)
                    .deliveredQuantity(dto.getDeliveredQuantity())
                    .orderedQuantity(dto.getOrderedQuantity())
                    .costPrice(dto.getCostPrice())
                    .purchasePrice(dto.getPurchasePrice())
                    .stock(stock)
                    .build();
            stockCommodityRepository.save(stockCommodity);
        }
    }

    private List<CommodityWithDetailsDao> commodityWithDetailsDao(List<StockCommodity> stockCommodities) {
        List<CommodityWithDetailsDao> commoditiesWithDetails = new ArrayList<>();

        for (StockCommodity stockCommodity : stockCommodities) {
            Commodity commodity = stockCommodity.getCommodity();
            int orderedQuantity = stockCommodity.getOrderedQuantity();
            int deliveredQuantity = stockCommodity.getDeliveredQuantity();
            int costPrice = stockCommodity.getCostPrice();
            int purchasePrice = stockCommodity.getPurchasePrice();

            CommodityWithDetailsDao dto = new CommodityWithDetailsDao(
                    commodity,
                    orderedQuantity,
                    deliveredQuantity,
                    costPrice,
                    purchasePrice);
            commoditiesWithDetails.add(dto);
        }

        return commoditiesWithDetails;
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    @Transactional
    public void createAssetsFromStock(Stock stock) throws NotFoundException {
        List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(stock.getId());

        for (StockCommodity stockCommodity : stockCommodities) {
            Commodity commodity = stockCommodity.getCommodity();
            AssetType assetType = commodity.getAssetType();

            // Only create assets for IT Equipment and Office Equipment
            if (assetType == null) continue;

            String typeName = assetType.getName();
            if (!("IT Equipment".equalsIgnoreCase(typeName)
                    || "Office Equipment".equalsIgnoreCase(typeName)
                    || "Fleet".equalsIgnoreCase((typeName))
            )) {
                continue;
            }

            int quantityToCreate = stockCommodity.getDeliveredQuantity();

            for (int i = 0; i < quantityToCreate; i++) {
                Asset asset = Asset.builder()
                        .assetName(commodity.getName())
                        .commodity(commodity)
                        .assetType(assetType)
                        .supplier(stock.getSupplier())
                        .branch(stock.getBranch())
                        .stock(stock)
                        .purchaseCost(String.valueOf(stockCommodity.getPurchasePrice()))
                        .costOfTheAsset(String.valueOf(stockCommodity.getCostPrice()))
                        .dateReceipt(stock.getCreateDate().toString()) // or use another meaningful date
                        .assetStatus(statusService.findOneStatus(8L)) // Assuming 1L is "Active" or similar
                        .build();

                assetRepository.save(asset);

                // Update the registered number
                stockCommodity.setRegisteredNumber(stockCommodity.getRegisteredNumber() + 1);
            }

            stockCommodityRepository.save(stockCommodity);
        }
    }

}
