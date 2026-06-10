/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.notification.Email;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.stockCommodity.StockCommodity;
import pride.bank.erp.stockCommodity.StockCommodityDTO;
import pride.bank.erp.stockCommodity.StockCommodityRepository;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.supplier.SupplierService;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;
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
    private final CurrentUserService currentUserService;

    // Status codes (stable across re-seeds) — replace brittle magic numeric IDs.
    private static final String STATUS_REQUIRE_UPDATE  = "requireUpdate";
    private static final String STATUS_STOCK_PENDING   = "stockPending";
    private static final String STATUS_STOCK_COMPLETED = "stockCompleted";
    private static final Long HEAD_OFFICE_BRANCH_ID = 1L;

    @Transactional
    public Page<StockDAO> fetchAllStocks(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String lpoNumber,
            Long stockStatusId,
            Boolean disposed,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        StockPage stockPage = StockPage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .pageNumber(pageNumber)
                    .sortBy(sortBy)
                    .build();

            StockSearchCriteria criteria = StockSearchCriteria.builder()
                    .lpoNumber(lpoNumber)
                    .stockStatusId(stockStatusId)
                    .disposed(disposed)
                    .startDate(startDate)
                    .endDate(endDate)
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

            List<StockDAO> stockDAOS = convertToStockDTOList(stocks.getContent());
            return new PageImpl<>(stockDAOS, stocks.getPageable(), stocks.getTotalElements());
    }

    private List<StockDAO> convertToStockDTOList(List<Stock> stocks) {
        return stocks.stream().map(stock ->
                StockDAO.builder()
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
                        .build()).toList();
    }


    @Transactional
    public Stock createStock(
            Stock request,
            Long supplierID,
            List<StockCommodityDTO> stockCommoditiesRequest
    ) throws NotFoundException {
        if (stockCommoditiesRequest == null || stockCommoditiesRequest.isEmpty()) {
            throw new IllegalArgumentException("Commodities list must not be null or empty.");
        }
        validateQuantities(stockCommoditiesRequest);

        Supplier supplier = supplierService.findSupplier(supplierID);
        if (supplier == null) {
            throw new NotFoundException("Supplier with ID " + supplierID + " not found.");
        }

        // Stock against the stocker's own branch (Admin store) rather than always Head Office.
        Branch branch = resolveStockBranch();

        Summation summation = calculateSums(stockCommoditiesRequest);
        Status status = summation.getDeliveredCommoditiesSummation() == summation.getOrderedCommoditiesSummation()
                ? statusService.findByStatusCode(STATUS_STOCK_COMPLETED)
                : statusService.findByStatusCode(STATUS_STOCK_PENDING);

        // GRN number is authoritative on the server — never trust a client-supplied value.
        String grnNumber = generateGrnNumber();

        Stock stock = Stock
                .builder()
                .referenceNumber(request.getReferenceNumber())
                .totalCost(request.getTotalCost())
                .balanceCost(request.getBalanceCost())
                .name(request.getName())
                .branch(branch)
                .lpoNumber(request.getLpoNumber())
                .grnNumber(grnNumber)
                .status(status)
                .canNotBeDeleted(false)   // becomes true once an asset from this stock is edited
                .disposed(false)
                .supplier(supplier)
                .build();

        Stock savedStock = stockRepository.save(stock);
        addCommoditiesToStock(savedStock, stockCommoditiesRequest);

        GrnReport savedGrnReport = grnReportRepository.save(
                GrnReport.builder().name(grnNumber).documentPath(null).stock(savedStock).build()
        );

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

        storeService.addCommoditiesToStore(commodities, branch.getId());
        savedStock.setCommodities(commodities);

        return savedStock;
    }

    /** Validates that every line has non-negative quantities and delivered never exceeds ordered. */
    private void validateQuantities(List<StockCommodityDTO> lines) {
        for (StockCommodityDTO dto : lines) {
            if (dto.getOrderedQuantity() < 0 || dto.getDeliveredQuantity() < 0) {
                throw new IllegalArgumentException("Quantities cannot be negative.");
            }
            if (dto.getDeliveredQuantity() > dto.getOrderedQuantity()) {
                throw new IllegalArgumentException(
                        "Delivered quantity cannot exceed ordered quantity for commodity " + dto.getCommodityId() + ".");
            }
        }
    }

    /** Stock is recorded against the current user's branch; falls back to Head Office. */
    private Branch resolveStockBranch() throws NotFoundException {
        try {
            User current = currentUserService.getCurrentUser();
            if (current != null && current.getBranch() != null && current.getBranch().getId() != null) {
                return current.getBranch();
            }
        } catch (Exception ignored) {
            // No authenticated user / branch — fall back to Head Office below.
        }
        Branch headOffice = branchService.findBranch(HEAD_OFFICE_BRANCH_ID);
        if (headOffice == null) {
            throw new NotFoundException("Head office branch not found.");
        }
        return headOffice;
    }

    private String generateGrnNumber() {
        return "GRN-" + System.currentTimeMillis();
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

            if (delivery.getDeliveredQuantity() < 0) {
                throw new IllegalArgumentException("Delivered quantity cannot be negative.");
            }
            int previousDelivered = existing.getDeliveredQuantity();
            int newDelivered = previousDelivered + delivery.getDeliveredQuantity();
            if (newDelivered > existing.getOrderedQuantity()) {
                throw new IllegalArgumentException(
                        "Total delivered would exceed ordered quantity for commodity " + delivery.getCommodityId() + ".");
            }
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
        stock.setStatus(
                updatedSummation.getDeliveredCommoditiesSummation() == updatedSummation.getOrderedCommoditiesSummation()
                        ? statusService.findByStatusCode(STATUS_STOCK_COMPLETED)
                        : statusService.findByStatusCode(STATUS_STOCK_PENDING)
        );
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
            // Prices are optional on a top-up delivery — fall back to the line's current unit prices.
            int unitCostPrice = delivery.getCostPrice() != null
                    ? delivery.getCostPrice() : stockCommodity.getCostPrice();
            int unitPurchasePrice = delivery.getPurchasePrice() != null
                    ? delivery.getPurchasePrice() : stockCommodity.getPurchasePrice();

            // This GRN line records THIS delivery; the "total*" fields snapshot the
            // cumulative position for the commodity after this delivery (quantities are
            // additive; unit prices are not summed).
            GrnCommodity grnCommodity = GrnCommodity.builder()
                    .grnReport(savedGrnReport)
                    .commodity(commodity)
                    .deliveredQuantity(deliveredQty)
                    .orderedQuantity(orderedQty)
                    .costPrice(unitCostPrice)
                    .purchasePrice(unitPurchasePrice)
                    .totalDeliveredQuantity(stockCommodity.getDeliveredQuantity())
                    .totalOrderedQuantity(stockCommodity.getOrderedQuantity())
                    .totalCostPrice(unitCostPrice)
                    .totalPurchasePrice(unitPurchasePrice)
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

    @Transactional
    public Stock updateStock(
            Long stockID,
            Stock request,
            Long statusID,
            Long supplierID,
            List<StockCommodityDTO> stockCommoditiesRequest
    ) throws NotFoundException {
        Stock stock = findOneStock(stockID);

        if (stockCommoditiesRequest == null || stockCommoditiesRequest.isEmpty()) {
            throw new IllegalArgumentException("Commodities list must not be null or empty.");
        }
        validateQuantities(stockCommoditiesRequest);

        if (supplierID != null) {
            Supplier supplier = supplierService.findSupplier(supplierID);
            if (supplier == null) {
                throw new NotFoundException("Supplier with ID " + supplierID + " not found.");
            }
            stock.setSupplier(supplier);
        }
        if (statusID != null) {
            stock.setStatus(statusService.findOneStatus(statusID));
        }

        stock.setReferenceNumber(request.getReferenceNumber());
        stock.setTotalCost(request.getTotalCost());
        stock.setBalanceCost(request.getBalanceCost());

        Stock savedStock = stockRepository.save(stock);

        // Merge in place instead of delete-and-recreate so registeredNumber / asset
        // linkage survives a correction.
        mergeStockCommodities(savedStock, stockCommoditiesRequest);

        List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(savedStock.getId());
        savedStock.setCommodities(commodityWithDetailsDao(stockCommodities));
        return savedStock;
    }

    /**
     * Non-destructive merge of a stock's commodity lines: existing lines are updated in place
     * (preserving {@code registeredNumber} and asset linkage), new lines are added, and lines no
     * longer present are removed only when no assets have been registered from them.
     */
    private void mergeStockCommodities(Stock stock, List<StockCommodityDTO> requested) throws NotFoundException {
        List<StockCommodity> existing = stockCommodityRepository.findByStockId(stock.getId());
        Map<Long, StockCommodity> existingByCommodity = existing.stream()
                .collect(Collectors.toMap(sc -> sc.getCommodity().getId(), sc -> sc, (a, b) -> a));

        Set<Long> requestedIds = new HashSet<>();
        for (StockCommodityDTO dto : requested) {
            requestedIds.add(dto.getCommodityId());
            StockCommodity sc = existingByCommodity.get(dto.getCommodityId());
            if (sc != null) {
                sc.setOrderedQuantity(dto.getOrderedQuantity());
                sc.setDeliveredQuantity(dto.getDeliveredQuantity());
                if (dto.getCostPrice() != null) sc.setCostPrice(dto.getCostPrice());
                if (dto.getPurchasePrice() != null) sc.setPurchasePrice(dto.getPurchasePrice());
                stockCommodityRepository.save(sc);
            } else {
                Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
                if (commodity == null) {
                    throw new NotFoundException("Commodity with ID " + dto.getCommodityId() + " not found.");
                }
                stockCommodityRepository.save(StockCommodity.builder()
                        .commodity(commodity)
                        .orderedQuantity(dto.getOrderedQuantity())
                        .deliveredQuantity(dto.getDeliveredQuantity())
                        .costPrice(dto.getCostPrice() != null ? dto.getCostPrice() : 0)
                        .purchasePrice(dto.getPurchasePrice() != null ? dto.getPurchasePrice() : 0)
                        .stock(stock)
                        .build());
            }
        }

        for (StockCommodity sc : existing) {
            if (!requestedIds.contains(sc.getCommodity().getId())) {
                if (sc.getRegisteredNumber() > 0) {
                    throw new IllegalArgumentException(
                            "Cannot remove commodity " + sc.getCommodity().getName()
                                    + " because assets have already been registered from it.");
                }
                stockCommodityRepository.delete(sc);
            }
        }
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
    private void createAssetsFromStock(Stock stock) throws NotFoundException {
        List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(stock.getId());

        Status requireUpdateStatus = statusService.findByStatusCode(STATUS_REQUIRE_UPDATE);

        // Group by asset type so we can fire one consolidated notification per
        // category to the team responsible (via AssetType.ownerGroupEmail).
        // Map of assetType -> { commodityName -> quantity }
        Map<AssetType, Map<String, Integer>> createdByType = new HashMap<>();

        for (StockCommodity stockCommodity : stockCommodities) {
            Commodity commodity = stockCommodity.getCommodity();
            AssetType assetType = commodity.getAssetType();

            if (assetType == null) continue;

            // Asset creation is opt-in per category via the tracksAssets flag. Consumables
            // (Stationery, Cleaning, Healthcare, etc.) leave it false and are skipped.
            if (!assetType.isTracksAssets()) {
                continue;
            }

            int availableQuantity = stockCommodity.getDeliveredQuantity() - stockCommodity.getRegisteredNumber();
            if (availableQuantity <= 0) {
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
                        .disposed(false)
                        .assetStatus(requireUpdateStatus) // "Require Update After Stocking"
                        .build();

                assetRepository.save(asset);

                stockCommodity.setRegisteredNumber(stockCommodity.getRegisteredNumber() + 1);
            }

            stockCommodityRepository.save(stockCommodity);

            createdByType
                    .computeIfAbsent(assetType, k -> new HashMap<>())
                    .merge(commodity.getName(), availableQuantity, Integer::sum);
        }

        // Fire one email per asset type. Recipient: the category's
        // ownerGroupEmail (CLAUDE.md: "the unit responsible for this should also
        // be customizable per asset category"). Fall back to the legacy
        // hardcoded routing for backwards compatibility while admins migrate.
        for (Map.Entry<AssetType, Map<String, Integer>> entry : createdByType.entrySet()) {
            AssetType type = entry.getKey();
            Map<String, Integer> items = entry.getValue();

            StringBuilder summary = new StringBuilder();
            items.forEach((name, qty) ->
                    summary.append("- ").append(qty).append(" ").append(name).append("<br/>"));

            String recipient = type.getOwnerGroupEmail();
            if (recipient == null || recipient.isBlank()) {
                log.warn("No ownerGroupEmail configured for asset category '{}' — skipping stocking notification",
                        type.getName());
                continue;
            }

            String greeting = "Dear " + type.getName() + " Team,";
            String body = buildAdminEmailBody(
                    summary.toString(),
                    "update the inventory of stocked " + type.getName() + " items"
            );
            notifyIndividual(recipient, stock, greeting, body);
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
        emailService.sendEmailVerification(
                email,
                String.format("Stock LPO ID: %s", stock.getLpoNumber()),
                null,
                updaterName,
                "View Stock",
                commoditySummary,
                "stocks",
                null,
                stock,
                null,
                null,
                null
        );
    }

    private String buildAdminEmailBody(
            String commodityDetails,
            String inventoryMessage
    ) {
        return "Kindly proceed to <strong>" + inventoryMessage + "</strong>, " +
                "and ensure that the items are <strong>properly prepared and ready for allocation or setup</strong> as needed.<br/>" +
                "Please confirm once the task has been completed.<br/><br/>" +
                commodityDetails + "<br/>";
    }

    @Transactional
    public DeleteResponse deleteStock(Long stockID) throws NotFoundException {
        Stock stock = findOneStock(stockID); // throws NotFoundException if not found

        // 🔒 Prevent deletion if at least one asset from this stock has been updated
        if (Boolean.TRUE.equals(stock.isCanNotBeDeleted())) {
            return DeleteResponse.builder()
                    .success(false)
                    .message(String.format(
                            "Stock with ID %d cannot be deleted because at least one of its assets has been updated.",
                            stockID
                    ))
                    .build();
        }

        // Step 1: Soft delete GrnReports and their GrnCommodities
        List<GrnReport> grnReports = grnReportRepository.findByStockId(stockID);
        for (GrnReport grnReport : grnReports) {
            // Soft delete each GrnCommodity linked to this GrnReport
            List<GrnCommodity> commodities = grnCommodityRepository.findByGrnReportId(grnReport.getId());
            for (GrnCommodity commodity : commodities) {
                commodity.setDisposed(true);
                commodity.setDisposalDate(LocalDateTime.now());
                grnCommodityRepository.save(commodity);
            }

            // Soft delete the GRN report
            grnReport.setDisposed(true);
            grnReport.setDisposalDate(LocalDateTime.now());
            grnReportRepository.save(grnReport);
        }

        // Step 2: Soft delete assets linked to the stock
        List<Asset> assets = assetRepository.findByStockId(stockID);
        for (Asset asset : assets) {
            asset.setDisposed(true);
            asset.setDisposalDate(LocalDateTime.now());
            assetRepository.save(asset);
        }

        // Step 3: Soft delete stock
        stock.setDisposed(true);
        stock.setDisposalDate(LocalDateTime.now());
        stockRepository.save(stock);

        return DeleteResponse.builder()
                .message(String.format("Stock with ID %d and its associated data have been marked as deleted.", stockID))
                .success(true)
                .build();
    }

}