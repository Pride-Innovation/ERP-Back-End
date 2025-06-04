/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.supplier.SupplierService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockService {
    private final StockRepository stockRepository;
    private final StatusService statusService;
    private final SupplierService supplierService;
    private final CommodityService commodityService;
    private final StockCommodityRepository stockCommodityRepository;
    private final BranchService branchService;

    @Transactional
    public Page<Stock> fetchAllStocks(Pageable pageable) {
        Page<Stock> stocks = stockRepository.findAll(pageable);

        // Collect all stock IDs to batch-fetch their commodities
        List<Long> stockIds = stocks.stream()
                .map(Stock::getId)
                .toList();

        // Fetch all StockCommodity entries for these stock IDs
        List<StockCommodity> allStockCommodities = stockCommodityRepository.findByStockIdIn(stockIds);

        // Group them by stock ID
        Map<Long, List<StockCommodity>> commoditiesByStockId = allStockCommodities.stream()
                .collect(Collectors.groupingBy(sc -> sc.getStock().getId()));

        // Populate the transient `commodities` field for each stock
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
            Long statusID,
            Long supplierID,
            List<StockCommodityDTO> stockCommoditiesRequest
    )
            throws NotFoundException {
        Status status = statusService.findOneStatus(statusID);
        Supplier supplier = supplierService.findSupplier(supplierID);
        Branch headOffice = branchService.findBranch(1L);

        Stock stock = Stock
                .builder()
                .referenceNumber(request.getReferenceNumber())
                .totalCost(request.getTotalCost())
                .balanceCost(request.getBalanceCost())
                .name(request.getName())
                .branch(headOffice)
                .status(status)
                .supplier(supplier)
                .build();

        Stock savedStock = stockRepository.save(stock);
        addCommoditiesToStock(savedStock, stockCommoditiesRequest);

        List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(savedStock.getId());
        List<CommodityWithDetailsDao> commodities = commodityWithDetailsDao(stockCommodities);
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
}
