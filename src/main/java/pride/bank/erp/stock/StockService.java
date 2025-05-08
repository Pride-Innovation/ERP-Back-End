/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
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

@Service
@RequiredArgsConstructor
public class StockService {
    private final StockRepository stockRepository;
    private final StatusService statusService;
    private final SupplierService supplierService;
    private final CommodityService commodityService;
    private final StockCommodityRepository stockCommodityRepository;

    public Page<Stock> fetchAllStocks(Pageable pageable) {
        return stockRepository.findAll(pageable);
    }

    public Stock createStock(
            Stock request,
            Long statusID,
            Long supplierID,
            List<StockCommodityDTO> stockCommoditiesRequest
    )
            throws NotFoundException {
        Status status = statusService.findOneStatus(statusID);
        Supplier supplier = supplierService.findSupplier(supplierID);

        Stock stock = Stock
                .builder()
                .referenceNumber(request.getReferenceNumber())
                .totalCost(request.getTotalCost())
                .balanceCost(request.getBalanceCost())
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
                    .actualCost(dto.getActualCost())
                    .expectedCost(dto.getExpectedCost())
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
            int expectedCost = stockCommodity.getExpectedCost();
            int actualCost = stockCommodity.getActualCost();

            CommodityWithDetailsDao dto = new CommodityWithDetailsDao(
                    commodity,
                    orderedQuantity,
                    deliveredQuantity,
                    expectedCost,
                    actualCost);
            commoditiesWithDetails.add(dto);
        }

        return commoditiesWithDetails;
    }
}
