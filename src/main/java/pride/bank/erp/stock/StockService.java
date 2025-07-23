/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import pride.bank.erp.grnCommodity.GrnCommodity;
import pride.bank.erp.grnCommodity.GrnCommodityRepository;
import pride.bank.erp.grnReport.GrnReport;
import pride.bank.erp.grnReport.GrnReportRepository;
import pride.bank.erp.helper.ExtractNumberFromEmailAddress;
import pride.bank.erp.notification.Email;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.stockCommodity.StockCommodity;
import pride.bank.erp.stockCommodity.StockCommodityDTO;
import pride.bank.erp.stockCommodity.StockCommodityRepository;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.supplier.SupplierService;

import java.util.*;
import java.util.stream.Collectors;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final GrnReportRepository grnReportRepository;
    private final GrnCommodityRepository grnCommodityRepository;
    private final Email emailService;

    @Value("${it-team.email.address}")
    private String itTeamEmailAddress;

    @Value("${admin-team.email.address}")
    private String adminTeamEmailAddress;

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
    ) throws NotFoundException {
        try {
            Supplier supplier = supplierService.findSupplier(supplierID);
            if (supplier == null) {
                throw new NotFoundException("Supplier with ID " + supplierID + " not found.");
            }

            Branch headOffice = branchService.findBranch(1L);
            if (headOffice == null) {
                throw new NotFoundException("Head office branch not found.");
            }

            if (stockCommoditiesRequest == null || stockCommoditiesRequest.isEmpty()) {
                throw new IllegalArgumentException("Commodities list must not be null or empty.");
            }

            Summation summation = calculateSums(stockCommoditiesRequest);

            Status status;
            if (summation.getDeliveredCommoditiesSummation() == summation.getOrderedCommoditiesSummation()) {
                status = statusService.findOneStatus(11L); // Completed
            } else {
                status = statusService.findOneStatus(10L); // Pending
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

            GrnReport grn = GrnReport.builder()
                    .name(savedStock.getGrnNumber())
                    .documentPath(null)
                    .stock(savedStock)
                    .build();
            GrnReport savedGrnReport = grnReportRepository.save(grn);

            for (StockCommodityDTO dto : stockCommoditiesRequest) {
                Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
                if (commodity == null) {
                    throw new NotFoundException("Commodity with ID " + dto.getCommodityId() + " not found.");
                }

                GrnCommodity grnCommodity = GrnCommodity.builder()
                        .grnReport(savedGrnReport)
                        .commodity(commodity)
                        .deliveredQuantity(dto.getDeliveredQuantity())
                        .orderedQuantity(dto.getOrderedQuantity())
                        .costPrice(dto.getCostPrice())
                        .purchasePrice(dto.getPurchasePrice())
                        .totalDeliveredQuantity(dto.getDeliveredQuantity())
                        .totalOrderedQuantity(dto.getOrderedQuantity())
                        .totalCostPrice(dto.getCostPrice())
                        .totalPurchasePrice(dto.getPurchasePrice())
                        .build();

                grnCommodityRepository.save(grnCommodity);
            }

            createAssetsFromStock(savedStock);

            List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(savedStock.getId());
            List<CommodityWithDetailsDao> commodities = commodityWithDetailsDao(stockCommodities);

            storeService.addCommoditiesToStore(commodities, 1L);
            savedStock.setCommodities(commodities);

            return savedStock;

        } catch (NotFoundException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid input: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error(ex.getMessage());
            throw new RuntimeException("Error creating stock: " + ex.getMessage(), ex);
        }
    }


    /**
     * Handles the partial or complete delivery of additional commodities for an existing stock entry.
     *
     * <p>This method updates the delivered quantity of each commodity, adjusts the store inventory with only
     * the newly delivered amounts, and recalculates the stock's status (partial or complete).
     *
     * <p>Additionally, this method:
     * <ul>
     *   <li>Updates the cost and purchase price if they have changed</li>
     *   <li>Generates a new GRN report associated with the stock and the current delivery</li>
     *   <li>Creates assets from newly delivered commodities if eligible</li>
     * </ul>
     *
     * @param stockId              the ID of the stock receiving the additional delivery
     * @param additionalDeliveries a list of commodity DTOs representing new deliveries
     * @param grnNumber            a unique identifier for the Goods Received Note (GRN) for this delivery
     * @return the updated stock object with current commodity and delivery information
     * @throws NotFoundException if the stock or commodities are not found
     */

    @Transactional
    public Stock completeDelivery(
            Long stockId,
            List<StockCommodityDTO> additionalDeliveries,
            String grnNumber
    ) throws NotFoundException {
        Stock stock = findOneStock(stockId);
        List<StockCommodity> existingCommodities = stockCommodityRepository.findByStockId(stockId);
        List<CommodityWithDetailsDao> newlyDelivered = new ArrayList<>();

        for (StockCommodityDTO delivery : additionalDeliveries) {
            StockCommodity existing = existingCommodities.stream()
                    .filter(sc -> sc.getCommodity().getId().equals(delivery.getCommodityId()))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Commodity not found in stock"));

            int previousDelivered = existing.getDeliveredQuantity();
            int newDelivered = previousDelivered + delivery.getDeliveredQuantity();
            existing.setDeliveredQuantity(newDelivered);

            if (delivery.getCostPrice() != null) {
                existing.setCostPrice(delivery.getCostPrice());
            }
            if (delivery.getPurchasePrice() != null) {
                existing.setPurchasePrice(delivery.getPurchasePrice());
            }

            stockCommodityRepository.save(existing);

            CommodityWithDetailsDao dao = CommodityWithDetailsDao.builder()
                    .commodity(existing.getCommodity())
                    .deliveredQuantity(delivery.getDeliveredQuantity())
                    .build();
            newlyDelivered.add(dao);
        }

        Summation updatedSummation = calculateSumsFromStock(stockId);
        if (updatedSummation.getDeliveredCommoditiesSummation() == updatedSummation.getOrderedCommoditiesSummation()) {
            stock.setStatus(statusService.findOneStatus(11L)); // Stock Completed Status
        } else {
            stock.setStatus(statusService.findOneStatus(10L)); // Stock Pending Status
        }
        stockRepository.save(stock);

        storeService.addCommoditiesToStore(newlyDelivered, stock.getBranch().getId());

        GrnReport grn = GrnReport.builder()
                .name(grnNumber)
                .documentPath(null)
                .stock(stock)
                .build();
        GrnReport savedGrnReport = grnReportRepository.save(grn);

        for (StockCommodityDTO delivery : additionalDeliveries) {
            Commodity commodity = commodityService.findCommodity(delivery.getCommodityId());

            StockCommodity stockCommodity = existingCommodities.stream()
                    .filter(sc -> sc.getCommodity().getId().equals(delivery.getCommodityId()))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Commodity not found in stock"));

            int deliveredQty = delivery.getDeliveredQuantity();
            int orderedQty = delivery.getOrderedQuantity();
            Integer costPrice = delivery.getCostPrice();
            Integer purchasePrice = delivery.getPurchasePrice();

            /*
             * We need to subtract the delivered Qty because the
             * StockCommodity is already updated with new stock at this time
             */
            int prevTotalDelivered = stockCommodity.getDeliveredQuantity() - deliveredQty;
            int prevTotalPrice = stockCommodity.getCostPrice();
            int prevTotalPurchasePrice = stockCommodity.getPurchasePrice();

            System.out.printf("Previous total delivered: %s", prevTotalDelivered);

            int totalDelivered = prevTotalDelivered + deliveredQty;
            Integer totalCostPrice = prevTotalPrice + costPrice;
            Integer totalPurchasePrice = prevTotalPurchasePrice + purchasePrice;

            GrnCommodity grnCommodity = GrnCommodity.builder()
                    .grnReport(savedGrnReport)
                    .commodity(commodity)
                    .deliveredQuantity(deliveredQty)
                    .orderedQuantity(orderedQty)
                    .costPrice(costPrice)
                    .purchasePrice(purchasePrice)
                    .totalPurchasePrice(totalPurchasePrice)
                    .totalCostPrice(totalCostPrice)
                    .totalOrderedQuantity(orderedQty)
                    .totalDeliveredQuantity(totalDelivered)
                    .build();
            grnCommodityRepository.save(grnCommodity);
        }

        createAssetsFromStock(stock);

        List<StockCommodity> updatedStockCommodities = stockCommodityRepository.findByStockId(stockId);
        List<CommodityWithDetailsDao> updatedDetails = commodityWithDetailsDao(updatedStockCommodities);
        stock.setCommodities(updatedDetails);

        return stock;
    }


    /**
     * Update Stock
     *
     * @param stockID                 this takes the stock Identification
     * @param request                 this is the request body
     * @param statusID                this is the status
     * @param supplierID              this takes the supplier.
     * @param stockCommoditiesRequest this takes the commodities
     * @return Stock
     * @throws NotFoundException This method is to update a stock.
     *                           Its major purpose to ensure that the system users correct mistakes during
     *                           stocking
     */

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
    public StockResponseDTO fetchSingleStock(Long stockId) throws NotFoundException {
        Stock stock = findOneStock(stockId);
        List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(stockId);
        List<CommodityWithDetailsDao> commodities = commodityWithDetailsDao(stockCommodities);
        stock.setCommodities(commodities);

        return StockResponseDTO
                .builder()
                .id(stock.getId())
                .referenceNumber(stock.getReferenceNumber())
                .name(stock.getName())
                .totalCost(stock.getTotalCost())
                .balanceCost(stock.getBalanceCost())
                .lpoNumber(stock.getLpoNumber())
                .status(stock.getStatus())
                .branch(stock.getBranch())
                .supplier(stock.getSupplier())
                .createDate(stock.getCreateDate())
                .lastModified(stock.getLastModified())
                .createdBy(stock.getCreatedBy())
                .lastModifiedBy(stock.getLastModifiedBy())
                .commodities(commodities)
                .grnNumber(stock.getGrnNumber())
                .grnReports(stock.getGrnReports())
                .build();
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

    /**
     * Creates asset records from stock commodities associated with the given stock.
     * <p>
     * Logic:
     * - For each stock commodity, if it has a valid asset type (e.g., IT Equipment, Office Equipment, Fleet),
     * and available quantity to register, create asset records equal to the unregistered quantity.
     * - Each new asset is built using details from the commodity and stock.
     * - The registered number for the stock commodity is incremented accordingly.
     * - Commodities without an asset type or of a non-asset category (e.g., Stationery) are skipped.
     * - All created assets are saved, and the stock commodity is updated and saved as well.
     * <p>
     * Assumptions:
     * - Status ID 8 represents "Active" or similar initial asset status.
     */
    @Transactional
    private void createAssetsFromStock(Stock stock) throws NotFoundException {
        List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(stock.getId());

        boolean itEquipmentCreated = false;
        boolean officeEquipmentCreated = false;

        StringBuilder itCommodities = new StringBuilder();
        StringBuilder officeCommodities = new StringBuilder();
        Map<String, Integer> itMap = new HashMap<>();
        Map<String, Integer> officeMap = new HashMap<>();

        for (StockCommodity stockCommodity : stockCommodities) {
            Commodity commodity = stockCommodity.getCommodity();
            AssetType assetType = commodity.getAssetType();

            if (assetType == null) continue;

            String typeName = assetType.getName();
            if (!("IT Equipment".equalsIgnoreCase(typeName)
                    || "Office Equipment".equalsIgnoreCase(typeName)
                    || "Fleet".equalsIgnoreCase(typeName))) {
                continue;
            }

            int availableQuantity = stockCommodity.getDeliveredQuantity() - stockCommodity.getRegisteredNumber();
            if (availableQuantity <= 0) {
                System.out.println("No available quantity to register for commodity: " + commodity.getName());
                continue;
            }

            for (int i = 0; i < availableQuantity; i++) {
                Asset asset = Asset.builder()
                        .assetName(commodity.getName())
                        .commodity(commodity)
                        .assetType(assetType)
                        .supplier(stock.getSupplier())
                        .branch(stock.getBranch())
                        .stock(stock)
                        .purchaseCost(String.valueOf(stockCommodity.getPurchasePrice()))
                        .costOfTheAsset(String.valueOf(stockCommodity.getCostPrice()))
                        .dateReceipt(stock.getCreateDate().toString())
                        .assetStatus(statusService.findOneStatus(9L)) // Require Update After Stocking Status ID
                        .build();

                assetRepository.save(asset);

                stockCommodity.setRegisteredNumber(stockCommodity.getRegisteredNumber() + 1);
            }

            stockCommodityRepository.save(stockCommodity);

            String commodityName = commodity.getName();

            if ("IT Equipment".equalsIgnoreCase(typeName)) {
                itMap.put(commodityName, itMap.getOrDefault(commodityName, 0) + availableQuantity);
                itEquipmentCreated = true;
            } else if ("Office Equipment".equalsIgnoreCase(typeName)) {
                officeMap.put(commodityName, officeMap.getOrDefault(commodityName, 0) + availableQuantity);
                officeEquipmentCreated = true;
            }
        }


        itMap.forEach((name, qty) -> itCommodities.append("- ").append(qty).append(" ").append(name).append("<br/>"));
        officeMap.forEach((name, qty) -> officeCommodities.append("- ").append(qty).append(" ").append(name).append("<br/>"));

        if (itEquipmentCreated) {
            notifyIndividual(
                    itTeamEmailAddress,
                    stock,
                    "IT Officer",
                    String.format("Please update the system with the following IT equipment:<br/><br/>%s", itCommodities)
            );
        }

        if (officeEquipmentCreated) {
            notifyIndividual(
                    adminTeamEmailAddress,
                    stock,
                    "Admin Officer",
                    String.format("Please update the system with the following Office equipment:<br/><br/>%s", officeCommodities)
            );
        }
    }

    private Summation calculateSumsFromStock(Long stockId) {
        List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(stockId);

        int orderedSum = 0;
        int deliveredSum = 0;

        for (StockCommodity sc : stockCommodities) {
            orderedSum += sc.getOrderedQuantity();
            deliveredSum += sc.getDeliveredQuantity();
        }

        return new Summation(orderedSum, deliveredSum);
    }


    private void notifyIndividual(
            String email,
            Stock stock,
            String updaterName,
            String commoditySummary
    ) {
        ExtractNumberFromEmailAddress cleanedEmail = new ExtractNumberFromEmailAddress(email);
        emailService.sendEmailVerification(
                cleanedEmail.getCleanedEmail(),
                String.format("Stock LPO ID: %s", stock.getLpoNumber()),
                null,
                updaterName,
                "View Stock",
                commoditySummary,
                "stocks",
                null,
                stock
        );
    }

}