/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
import pride.bank.erp.inventory.ledger.LedgerContext;
import pride.bank.erp.inventory.ledger.StockLedgerReason;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.stockCommodity.StockCommodity;
import pride.bank.erp.stockCommodity.StockCommodityDTO;
import pride.bank.erp.stockCommodity.StockCommodityRepository;
import pride.bank.erp.stocktake.StockReversalService;
import pride.bank.erp.stockReceipt.StockReceipt;
import pride.bank.erp.stockReceipt.StockReceiptDTO;
import pride.bank.erp.stockReceipt.StockReceiptRepository;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.supplier.SupplierService;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {
    private final StockRepository stockRepository;

    /** The one resolver for how far a person may see — see AccessScopeService. */
    private final pride.bank.erp.access.AccessScopeService accessScopeService;
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
    private final StockReceiptRepository stockReceiptRepository;
    private final CurrentUserService currentUserService;
    private final ApplicationEventPublisher eventPublisher;
    private final StockReversalService stockReversalService;
    private final pride.bank.erp.auditing.trail.AuditService auditService;

    // Status codes (stable across re-seeds) — replace brittle magic numeric IDs.
    private static final String STATUS_REQUIRE_UPDATE  = "requireUpdate";
    private static final String STATUS_STOCK_PENDING   = "stockPending";
    private static final String STATUS_STOCK_COMPLETED = "stockCompleted";
    private static final String STATUS_STOCK_CLOSED_SHORT = "stockClosedShort";
    private static final Long HEAD_OFFICE_BRANCH_ID = 1L;

    @Transactional
    public Page<StockDAO> fetchAllStocks(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String lpoNumber,
            Long stockStatusId,
            Long supplierId,
            Long branchId,
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
                    .scopedBranchId(visibleBranchId(branchId))
                    .lpoNumber(lpoNumber)
                    .stockStatusId(stockStatusId)
                    .supplierId(supplierId)
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
                        .poNumber(stock.getPoNumber())
                        .orderDate(stock.getOrderDate())
                        .deliveryDate(stock.getDeliveryDate())
                        .invoiceDate(stock.getInvoiceDate())
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
                        /*
                         * Which branch this receipt belongs to.
                         *
                         * `Stock.branch` is @JsonIgnore on the entity, so the client never saw it.
                         * That was tolerable while every user saw one branch's stock; once a unit
                         * confers cross-branch reach, an Admin officer looking at a mixed list has
                         * no way to tell Gulu's deliveries from Kampala's — and the row menu cannot
                         * scope its actions per record without it.
                         */
                        .branch(stock.getBranch())
                        .supplier(stock.getSupplier() != null ?
                                Supplier.builder()
                                        // Carried so a client can act on the supplier rather than
                                        // only print it — the inventory filter matches on the id,
                                        // and without this the row cannot say which supplier it is
                                        // in any way a query could use.
                                        .id(stock.getSupplier().getId())
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
            List<StockCommodityDTO> stockCommoditiesRequest,
            String idempotencyKey
    ) throws NotFoundException {
        if (stockCommoditiesRequest == null || stockCommoditiesRequest.isEmpty()) {
            throw new IllegalArgumentException("Commodities list must not be null or empty.");
        }

        // Idempotency: a retried/double-submitted create resolves to the already-created
        // stock instead of duplicating it (and its assets). Keyed on a stable client UUID.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Stock> existing = stockRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Stock prior = existing.get();
                List<StockCommodity> priorCommodities = stockCommodityRepository.findByStockId(prior.getId());
                prior.setCommodities(commodityWithDetailsDao(priorCommodities));
                return prior;
            }
        }

        validateQuantities(stockCommoditiesRequest);

        Supplier supplier = supplierService.findSupplier(supplierID);
        if (supplier == null) {
            throw new NotFoundException("Supplier with ID " + supplierID + " not found.");
        }

        // Stock against the stocker's own branch (Admin store) rather than always Head Office.
        Branch branch = resolveStockBranch();

        /*
         * Belt and braces: the branch already comes from the stocker, so this cannot refuse anyone
         * today. It is here so the rule survives a refactor — the moment someone lets the branch be
         * supplied by the caller, this is what stops a receipt being filed against another branch.
         */
        accessScopeService.requireCanManage(
                pride.bank.erp.access.ScopeSubject.INVENTORY,
                branch != null ? branch.getId() : null,
                null);

        Summation summation = calculateSums(stockCommoditiesRequest);
        Status status = summation.getDeliveredCommoditiesSummation() == summation.getOrderedCommoditiesSummation()
                ? statusService.findByStatusCode(STATUS_STOCK_COMPLETED)
                : statusService.findByStatusCode(STATUS_STOCK_PENDING);

        Stock stock = Stock
                .builder()
                .referenceNumber(request.getReferenceNumber())
                .totalCost(request.getTotalCost())
                .balanceCost(request.getBalanceCost())
                .name(request.getName())
                .branch(branch)
                .lpoNumber(request.getLpoNumber())
                .poNumber(request.getPoNumber())
                .orderDate(request.getOrderDate())
                .deliveryDate(request.getDeliveryDate())
                .invoiceDate(request.getInvoiceDate())
                .idempotencyKey(idempotencyKey)
                .status(status)
                .canNotBeDeleted(false)   // becomes true once an asset from this stock is edited
                .disposed(false)
                .supplier(supplier)
                .build();

        Stock savedStock = stockRepository.save(stock);
        addCommoditiesToStock(savedStock, stockCommoditiesRequest);

        // Anything already delivered at creation time is itself a physical receipt, so it goes
        // through exactly the same path as a later batch: one receipt, one GRN, one store credit,
        // one set of assets. An order captured with nothing yet delivered produces no GRN at all —
        // previously a "zero delivery" GRN was issued regardless, which put an empty document into
        // the audit trail.
        List<StockCommodityDTO> initiallyDelivered = stockCommoditiesRequest.stream()
                .filter(dto -> dto.getDeliveredQuantity() > 0)
                .toList();

        if (!initiallyDelivered.isEmpty()) {
            StockReceiptDTO openingReceipt = new StockReceiptDTO();
            openingReceipt.setLines(initiallyDelivered);
            openingReceipt.setReceiptDate(request.getDeliveryDate());
            openingReceipt.setInvoiceNumber(request.getReferenceNumber());
            openingReceipt.setIdempotencyKey(
                    idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey + ":opening" : null);
            // The lines were persisted above with their delivered quantity already set, so the
            // receipt must not add them a second time.
            recordReceipt(savedStock, openingReceipt, false);
        } else {
            recomputeStatus(savedStock);
            stockRepository.save(savedStock);
        }

        List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(savedStock.getId());
        savedStock.setCommodities(commodityWithDetailsDao(stockCommodities));

        // Any opening delivery above is recorded separately by recordReceipt, so a stock created with
        // goods already in hand produces both the order and its first GRN on the trail.
        auditService.record(pride.bank.erp.auditing.trail.AuditEventType.CREATED,
                pride.bank.erp.auditing.trail.AuditModule.INVENTORY, "STOCK_ORDER_CREATED",
                "Raised purchase order " + savedStock.getLpoNumber()
                        + " with " + stockCommodities.size() + " commodity line(s)",
                pride.bank.erp.auditing.trail.AuditSeverity.INFO,
                "Stock", savedStock.getId());

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

    /**
     * Issues a new GRN report for a stock and stamps it with a server-authoritative GRN number
     * derived from the report's own (monotonic, unique) id. Two-step save: persist to obtain the
     * id, then set the human-readable number. Replaces the old {@code "GRN-"+millis} scheme which
     * was neither sequential nor collision-safe.
     */
    private GrnReport issueGrnReport(Stock stock, StockReceipt receipt) {
        GrnReport report = grnReportRepository.save(
                GrnReport.builder().documentPath(null).stock(stock).stockReceipt(receipt).build()
        );
        report.setName(formatGrnNumber(report.getId(), report.getCreateDate()));
        return grnReportRepository.save(report);
    }

    private String formatGrnNumber(Long reportId, LocalDateTime when) {
        int year = (when != null ? when : LocalDateTime.now()).getYear();
        return String.format("GRN/%d/%06d", year, reportId);
    }


    /**
     * Records one <b>physical delivery</b> against an existing order — the single path by which
     * quantities ever enter the system after creation.
     *
     * <p>Everything a receipt touches moves together, inside one transaction: the delivered
     * quantity on each line, the store balance, the asset register, the GRN, and the stock's status.
     * That atomicity is the whole point — the previous split between this path and
     * {@code updateStock} let a storekeeper raise the delivered figure without any of the rest
     * following, so the screen said the goods had arrived while the store and asset register did
     * not know about them.
     *
     * <p>Quantities in {@code request.lines} are <b>deltas</b>: what arrived in this batch, never
     * the new cumulative total.
     *
     * @param stockId the order the batch was delivered against
     * @param request the batch: per-commodity quantities plus the delivery-note level details
     * @return the stock with refreshed commodity totals
     */
    @Transactional
    public Stock receiveDelivery(Long stockId, StockReceiptDTO request) throws NotFoundException {
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("A receipt must contain at least one commodity line.");
        }

        // Replaying the same submission returns the original receipt untouched rather than
        // receiving the batch a second time.
        String key = request.getIdempotencyKey();
        if (key != null && !key.isBlank()) {
            Optional<StockReceipt> prior = stockReceiptRepository.findByIdempotencyKey(key);
            if (prior.isPresent()) {
                Stock stock = prior.get().getStock();
                stock.setCommodities(commodityWithDetailsDao(
                        stockCommodityRepository.findByStockId(stock.getId())));
                return stock;
            }
        }

        Stock stock = findOneStock(stockId);
        requireStockInScope(stock, pride.bank.erp.access.ScopeAction.MANAGE);
        recordReceipt(stock, request, true);

        stock.setCommodities(commodityWithDetailsDao(stockCommodityRepository.findByStockId(stockId)));
        return stock;
    }

    /**
     * Backwards-compatible entry point for the old per-commodity "Receive more" call. Wraps the
     * lines into a receipt so legacy clients still produce a well-formed GRN.
     *
     * @deprecated use {@link #receiveDelivery(Long, StockReceiptDTO)} — it captures the delivery
     * note, invoice, receipt date and receiving officer that a GRN is supposed to carry.
     */
    @Deprecated
    @Transactional
    public Stock completeDelivery(
            Long stockId,
            List<StockCommodityDTO> additionalDeliveries,
            String grnNumber
    ) throws NotFoundException {
        StockReceiptDTO request = new StockReceiptDTO();
        request.setLines(additionalDeliveries);
        return receiveDelivery(stockId, request);
    }

    /**
     * The one place a delivery is applied. Shared by order creation (for whatever arrived up
     * front) and by every later batch.
     *
     * @param applyQuantityDelta {@code true} to add each line's quantity to the running delivered
     *                           total; {@code false} when the caller has already persisted the
     *                           quantities (order creation) and the receipt is only documenting them.
     */
    private StockReceipt recordReceipt(Stock stock, StockReceiptDTO request, boolean applyQuantityDelta)
            throws NotFoundException {

        List<StockCommodity> existingLines = stockCommodityRepository.findByStockId(stock.getId());
        Map<Long, StockCommodity> linesByCommodity = existingLines.stream()
                .collect(Collectors.toMap(sc -> sc.getCommodity().getId(), sc -> sc, (a, b) -> a));

        // Ignore empty lines so a receipt form submitted with most rows left blank records only
        // what actually arrived, instead of a GRN padded with zero-quantity entries.
        List<StockCommodityDTO> received = request.getLines().stream()
                .filter(line -> line.getDeliveredQuantity() > 0)
                .toList();

        if (received.isEmpty()) {
            throw new IllegalArgumentException("A receipt must record a quantity greater than zero for at least one commodity.");
        }

        LocalDateTime receiptDate = request.getReceiptDate() != null
                ? request.getReceiptDate()
                : LocalDateTime.now();

        Store destination = storeService.resolveAdminStore(stock.getBranch());

        StockReceipt receipt = stockReceiptRepository.save(StockReceipt.builder()
                .stock(stock)
                .receiptDate(receiptDate)
                .deliveryNoteNumber(trimToNull(request.getDeliveryNoteNumber()))
                .invoiceNumber(trimToNull(request.getInvoiceNumber()))
                .notes(trimToNull(request.getNotes()))
                .store(destination)
                .idempotencyKey(trimToNull(request.getIdempotencyKey()))
                .build());

        GrnReport grnReport = issueGrnReport(stock, receipt);
        List<CommodityWithDetailsDao> creditToStore = new ArrayList<>();

        for (StockCommodityDTO line : received) {
            StockCommodity stockLine = linesByCommodity.get(line.getCommodityId());
            if (stockLine == null) {
                throw new NotFoundException(
                        "Commodity with ID " + line.getCommodityId() + " is not on this order.");
            }

            if (applyQuantityDelta) {
                int newDelivered = stockLine.getDeliveredQuantity() + line.getDeliveredQuantity();
                if (newDelivered > stockLine.getOrderedQuantity()) {
                    throw new IllegalArgumentException(String.format(
                            "Receiving %d more %s would take the delivered total to %d against %d ordered.",
                            line.getDeliveredQuantity(), stockLine.getCommodity().getName(),
                            newDelivered, stockLine.getOrderedQuantity()));
                }
                stockLine.setDeliveredQuantity(newDelivered);
            }

            // A batch may be priced differently from the last one. The line's unit price is updated
            // so assets created from THIS batch carry this batch's price; assets already created
            // keep the price they were stamped with, and the GRN below records what this batch cost.
            if (line.getCostPrice() != null) {
                stockLine.setCostPrice(line.getCostPrice());
            }
            if (line.getPurchasePrice() != null) {
                stockLine.setPurchasePrice(line.getPurchasePrice());
            }
            stockCommodityRepository.save(stockLine);

            BigDecimal unitCost = stockLine.getCostPrice();
            BigDecimal unitPurchase = stockLine.getPurchasePrice();

            grnCommodityRepository.save(GrnCommodity.builder()
                    .grnReport(grnReport)
                    .commodity(stockLine.getCommodity())
                    .deliveredQuantity(line.getDeliveredQuantity())
                    // Ordered is taken from the order itself, not from the request — a client that
                    // echoed back the wrong figure used to make the GRN misstate the order.
                    .orderedQuantity(stockLine.getOrderedQuantity())
                    .costPrice(unitCost)
                    .purchasePrice(unitPurchase)
                    .totalDeliveredQuantity(stockLine.getDeliveredQuantity())
                    .totalOrderedQuantity(stockLine.getOrderedQuantity())
                    .totalCostPrice(unitCost)
                    .totalPurchasePrice(unitPurchase)
                    .build());

            creditToStore.add(CommodityWithDetailsDao.builder()
                    .commodity(stockLine.getCommodity())
                    .deliveredQuantity(line.getDeliveredQuantity())
                    .orderedQuantity(stockLine.getOrderedQuantity())
                    .costPrice(unitCost)
                    .purchasePrice(unitPurchase)
                    .build());
        }

        storeService.addCommoditiesToStore(creditToStore, stock.getBranch().getId(),
                LedgerContext.of(StockLedgerReason.RECEIPT, "STOCK_RECEIPT", receipt.getId(),
                        "GRN " + grnReport.getName()));
        createAssetsFromStock(stock, receiptDate);

        // The first GRN issued against an order remains the one shown on the stock header, so the
        // list view keeps a stable reference; every GRN is listed on the stock's own GRN tab.
        if (stock.getGrnNumber() == null || stock.getGrnNumber().isBlank()) {
            stock.setGrnNumber(grnReport.getName());
        }
        recomputeStatus(stock);
        stockRepository.save(stock);

        auditService.record(pride.bank.erp.auditing.trail.AuditEventType.CREATED,
                pride.bank.erp.auditing.trail.AuditModule.INVENTORY, "STOCK_RECEIVED",
                "Received " + grnReport.getName() + " against LPO " + stock.getLpoNumber()
                        + " into " + destination.getName()
                        + " — " + creditToStore.size() + " commodity line(s)",
                pride.bank.erp.auditing.trail.AuditSeverity.INFO,
                "Stock", stock.getId());

        return receipt;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Derives the stock's status from its own lines rather than trusting a client-supplied id.
     *
     * <p>A stock that was closed short stays closed short until a later receipt actually completes
     * it — receiving a further batch against a closed-short order should not silently reopen it as
     * "pending" while the outstanding balance is still not expected.
     */
    private void recomputeStatus(Stock stock) throws NotFoundException {
        Summation summation = calculateSumsFromStock(stock.getId());
        boolean fullyDelivered =
                summation.getDeliveredCommoditiesSummation() == summation.getOrderedCommoditiesSummation();

        if (fullyDelivered) {
            stock.setStatus(statusService.findByStatusCode(STATUS_STOCK_COMPLETED));
            return;
        }

        boolean closedShort = stock.getStatus() != null
                && STATUS_STOCK_CLOSED_SHORT.equals(stock.getStatus().getStatus());
        if (!closedShort) {
            stock.setStatus(statusService.findByStatusCode(STATUS_STOCK_PENDING));
        }
    }


    /**
     * Corrects an existing order: its header (supplier, references, business dates), what was
     * <b>ordered</b>, and unit prices.
     *
     * <p>It deliberately cannot change what has been <b>delivered</b>. Receiving is a physical
     * event with consequences that have to happen together — the store is credited, assets are
     * registered, a GRN is issued — and none of that can be inferred from someone typing a larger
     * number into a correction form. Raising the delivered figure here used to do exactly that:
     * the line said the goods had arrived while the store balance, the asset register and the GRN
     * trail all still reflected the earlier delivery, and the gap never closed. Deliveries go
     * through {@link #receiveDelivery(Long, StockReceiptDTO)}.
     *
     * <p>The status is derived from the lines afterwards, so correcting an order down to what has
     * actually been received completes it rather than leaving it stuck on "pending".
     */
    @Transactional
    public Stock updateStock(
            Long stockID,
            Stock request,
            Long supplierID,
            List<StockCommodityDTO> stockCommoditiesRequest
    ) throws NotFoundException {
        Stock stock = findOneStock(stockID);
        requireStockInScope(stock, pride.bank.erp.access.ScopeAction.MANAGE);

        if (stockCommoditiesRequest == null || stockCommoditiesRequest.isEmpty()) {
            throw new IllegalArgumentException("Commodities list must not be null or empty.");
        }

        if (supplierID != null) {
            Supplier supplier = supplierService.findSupplier(supplierID);
            if (supplier == null) {
                throw new NotFoundException("Supplier with ID " + supplierID + " not found.");
            }
            stock.setSupplier(supplier);
        }

        stock.setReferenceNumber(request.getReferenceNumber());
        stock.setTotalCost(request.getTotalCost());
        stock.setBalanceCost(request.getBalanceCost());
        if (request.getPoNumber() != null) stock.setPoNumber(request.getPoNumber());
        if (request.getOrderDate() != null) stock.setOrderDate(request.getOrderDate());
        if (request.getDeliveryDate() != null) stock.setDeliveryDate(request.getDeliveryDate());
        if (request.getInvoiceDate() != null) stock.setInvoiceDate(request.getInvoiceDate());

        Stock savedStock = stockRepository.save(stock);

        // Merge in place instead of delete-and-recreate so registeredNumber / asset
        // linkage survives a correction.
        mergeStockCommodities(savedStock, stockCommoditiesRequest);

        recomputeStatus(savedStock);
        savedStock = stockRepository.save(savedStock);

        List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(savedStock.getId());
        savedStock.setCommodities(commodityWithDetailsDao(stockCommodities));
        return savedStock;
    }

    /**
     * Non-destructive merge of a stock's commodity lines: existing lines are updated in place
     * (preserving {@code deliveredQuantity}, {@code registeredNumber} and asset linkage), new lines
     * are added, and lines are removed only when nothing has been received against them.
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
                if (dto.getOrderedQuantity() < 0) {
                    throw new IllegalArgumentException("Ordered quantity cannot be negative.");
                }
                // Correcting the order down below what the supplier has already delivered would
                // leave the line permanently over-delivered and its status un-derivable.
                if (dto.getOrderedQuantity() < sc.getDeliveredQuantity()) {
                    throw new IllegalArgumentException(String.format(
                            "Ordered quantity for %s cannot be set to %d — %d has already been received.",
                            sc.getCommodity().getName(), dto.getOrderedQuantity(), sc.getDeliveredQuantity()));
                }
                // Delivered is owned by the receipt trail, not by this form. Reject rather than
                // silently ignore, so a client that still submits it finds out.
                if (dto.getDeliveredQuantity() != sc.getDeliveredQuantity()) {
                    throw new IllegalArgumentException(String.format(
                            "Delivered quantity for %s cannot be changed here (currently %d). "
                                    + "Record a delivery against this order instead, so the store, "
                                    + "the asset register and the GRN are updated with it.",
                            sc.getCommodity().getName(), sc.getDeliveredQuantity()));
                }

                sc.setOrderedQuantity(dto.getOrderedQuantity());
                if (dto.getCostPrice() != null) sc.setCostPrice(dto.getCostPrice());
                if (dto.getPurchasePrice() != null) sc.setPurchasePrice(dto.getPurchasePrice());
                stockCommodityRepository.save(sc);
            } else {
                // A line added by correction is an addition to the ORDER; nothing has been
                // received against it yet, so it starts at zero delivered whatever was submitted.
                if (dto.getOrderedQuantity() <= 0) {
                    throw new IllegalArgumentException("Ordered quantity must be greater than zero.");
                }
                Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
                if (commodity == null) {
                    throw new NotFoundException("Commodity with ID " + dto.getCommodityId() + " not found.");
                }
                stockCommodityRepository.save(StockCommodity.builder()
                        .commodity(commodity)
                        .orderedQuantity(dto.getOrderedQuantity())
                        .deliveredQuantity(0)
                        .costPrice(dto.getCostPrice() != null ? dto.getCostPrice() : BigDecimal.ZERO)
                        .purchasePrice(dto.getPurchasePrice() != null ? dto.getPurchasePrice() : BigDecimal.ZERO)
                        .stock(stock)
                        .build());
            }
        }

        for (StockCommodity sc : existing) {
            if (!requestedIds.contains(sc.getCommodity().getId())) {
                // registeredNumber only ever moves for asset-tracked categories, so it used to let
                // a consumable line (Stationery, Cleaning …) be deleted while its delivered units
                // stayed on the store balance for good. Delivered quantity is the honest test.
                if (sc.getDeliveredQuantity() > 0) {
                    throw new IllegalArgumentException(String.format(
                            "Cannot remove %s — %d unit(s) have already been received against it and are held in the store.",
                            sc.getCommodity().getName(), sc.getDeliveredQuantity()));
                }
                if (sc.getRegisteredNumber() > 0) {
                    throw new IllegalArgumentException(
                            "Cannot remove commodity " + sc.getCommodity().getName()
                                    + " because assets have already been registered from it.");
                }
                stockCommodityRepository.delete(sc);
            }
        }
    }

    /**
     * The branch whose stock this caller may see, or null for every branch.
     *
     * <p>A requested branch is honoured only at ALL scope. Below it, naming another branch is
     * refused rather than quietly rewritten to the caller's own — showing one branch's deliveries
     * under another's name would be worse than saying no.
     *
     * <p><b>Floored at BRANCH, never SELF.</b> A stock receipt belongs to a branch and to no
     * individual — nobody is "assigned" a delivery — so resolving to SELF would show a storekeeper
     * an empty page rather than their own branch's deliveries. The resolver returns the caller's own
     * branch below ALL scope, which is already the answer we want; the floor matters only in that
     * this module must never narrow further.
     */
    private Long visibleBranchId(Long requestedBranchId) {
        return accessScopeService.resolveBranch(
                pride.bank.erp.access.ScopeSubject.INVENTORY, requestedBranchId);
    }

    /**
     * Refuses unless this stock record is within the caller's reach.
     *
     * <p>The listing alone is not a boundary. Before this, a branch storekeeper could not <em>find</em>
     * another branch's delivery through the page, but could open it, correct it, receive against it,
     * close it short or delete it by id — the same hole the assets module had, in a module that
     * moves money and goods.
     *
     * <p>Written out at each entry point rather than folded into {@link #findOneStock}, because that
     * loader is also how the issuance and asset-creation flows reach a stock while acting for the
     * system rather than for the person who triggered them.
     */
    private void requireStockInScope(Stock stock, pride.bank.erp.access.ScopeAction action) {
        Long branchId = stock.getBranch() != null ? stock.getBranch().getId() : null;

        if (action == pride.bank.erp.access.ScopeAction.MANAGE) {
            accessScopeService.requireCanManage(
                    pride.bank.erp.access.ScopeSubject.INVENTORY, branchId, null);
        } else {
            accessScopeService.requireCanView(
                    pride.bank.erp.access.ScopeSubject.INVENTORY, branchId, null);
        }
    }

    public Stock findOneStock(Long id) throws NotFoundException {
        return stockRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("No stock with id %s is found", id))
        );
    }

    /**
     * Closes a partially-delivered stock short: the outstanding quantity is accepted as never
     * arriving, so the record leaves {@code stockPending} for the terminal {@code stockClosedShort}
     * state (instead of sitting outstanding indefinitely). Already-received units and their assets
     * are untouched.
     */
    @Transactional
    public Stock closeShort(Long stockId, String reason) throws NotFoundException {
        Stock stock = findOneStock(stockId);
        requireStockInScope(stock, pride.bank.erp.access.ScopeAction.MANAGE);

        Summation summation = calculateSumsFromStock(stockId);
        if (summation.getDeliveredCommoditiesSummation() == summation.getOrderedCommoditiesSummation()) {
            throw new IllegalArgumentException("Stock is already fully delivered and cannot be closed short.");
        }

        stock.setStatus(statusService.findByStatusCode(STATUS_STOCK_CLOSED_SHORT));
        stock.setCloseShortReason(reason);
        stockRepository.save(stock);

        List<StockCommodity> stockCommodities = stockCommodityRepository.findByStockId(stockId);
        stock.setCommodities(commodityWithDetailsDao(stockCommodities));
        return stock;
    }

    @Transactional
    public StockResponseDTO fetchSingleStock(Long stockId) throws NotFoundException {
        Stock stock = findOneStock(stockId);
        requireStockInScope(stock, pride.bank.erp.access.ScopeAction.VIEW);
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
                .poNumber(stock.getPoNumber())
                .orderDate(stock.getOrderDate())
                .deliveryDate(stock.getDeliveryDate())
                .invoiceDate(stock.getInvoiceDate())
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
            BigDecimal costPrice = stockCommodity.getCostPrice();
            BigDecimal purchasePrice = stockCommodity.getPurchasePrice();

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
    private void createAssetsFromStock(Stock stock, LocalDateTime receiptDate) throws NotFoundException {
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
                        .purchaseCost(stockCommodity.getPurchasePrice())
                        .costOfTheAsset(stockCommodity.getCostPrice())
                        // The date THIS batch physically arrived — the basis for the asset's age and
                        // depreciation. Previously taken from the mutable Stock.deliveryDate header,
                        // which every later receipt or correction overwrote, silently rewriting the
                        // depreciation start date of assets received months earlier.
                        .dateReceipt(receiptDate.toString())
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
            // Published now, delivered after the transaction commits — a dead SMTP host must not
            // be able to roll back a delivery that physically happened.
            eventPublisher.publishEvent(new AssetsStockedEvent(recipient, stock, greeting, body));
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
        requireStockInScope(stock, pride.bank.erp.access.ScopeAction.MANAGE);

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

        // Step 0: take the stock back out of the store BEFORE anything is soft-deleted, so the
        // reversal is computed from live data and a failure (goods already issued out) aborts the
        // whole delete rather than leaving a half-deleted order behind.
        Map<Commodity, Integer> received = new LinkedHashMap<>();
        for (StockCommodity line : stockCommodityRepository.findByStockId(stockID)) {
            if (line.getDeliveredQuantity() > 0) {
                received.merge(line.getCommodity(), line.getDeliveredQuantity(), Integer::sum);
            }
        }
        if (!received.isEmpty()) {
            stockReversalService.reverse(
                    storeService.resolveAdminStore(stock.getBranch()),
                    received,
                    "Reversal of deleted stock " + (stock.getLpoNumber() != null
                            ? stock.getLpoNumber() : "#" + stockID));
        }

        // Step 1b: Soft delete the receipts those GRNs documented
        for (StockReceipt receipt : stockReceiptRepository.findByStockIdOrderByReceiptDateAsc(stockID)) {
            receipt.setDisposed(true);
            receipt.setDisposalDate(LocalDateTime.now());
            // Free the idempotency key so a corrected re-entry of the same delivery is not
            // rejected as a replay of the deleted one.
            receipt.setIdempotencyKey(null);
            stockReceiptRepository.save(receipt);
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

        auditService.record(pride.bank.erp.auditing.trail.AuditEventType.DELETED,
                pride.bank.erp.auditing.trail.AuditModule.INVENTORY, "STOCK_DELETED",
                "Deleted purchase order " + stock.getLpoNumber() + " and its GRNs",
                pride.bank.erp.auditing.trail.AuditSeverity.WARNING,
                "Stock", stockID);

        return DeleteResponse.builder()
                .message(String.format("Stock with ID %d and its associated data have been marked as deleted.", stockID))
                .success(true)
                .build();
    }

}