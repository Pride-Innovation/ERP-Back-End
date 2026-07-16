/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistory;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeService;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchService;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.FileUpload;
import pride.bank.erp.notification.Email;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.stock.Stock;
import pride.bank.erp.stock.StockRepository;
import pride.bank.erp.stockCommodity.StockCommodity;
import pride.bank.erp.stockCommodity.StockCommodityRepository;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.supplier.SupplierService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@Service
@RequiredArgsConstructor
public class AssetService {
    private final AssetRepository assetRepository;
    private final BranchService branchService;
    private final UserService userService;
    private final StatusService statusService;
    private final AssetTypeService assetTypeService;
    private final SupplierService supplierService;
    private final AssetSearchDao assetSearchDao;
    private final CommodityService commodityService;
    private final StockRepository stockRepository;
    private final StockCommodityRepository stockCommodityRepository;
    private final AssetAssignmentHistoryRepository assetAssignmentHistoryRepository;
    private final CurrentUserService currentUserService;
    private final FileUpload fileUpload;
    private final Email email;

    /** Upper bound on assets scanned in-memory when computing the derived "due for disposal" list. */
    private static final int DISPOSAL_SCAN_LIMIT = 10_000;

    public Page<AssetDAO> fetchAllAssets(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String assetName,
            Long assetTypeId,
            Long assetStatusId,
            Long commodityId,
            String engravedNumber,
            Long assignedToId,
            Boolean disposed,
            String model,
            String assignedTo,
            String location,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Boolean dueForDisposal
    ) {

        try {
            AssetPage assetPage = AssetPage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .pageNumber(pageNumber)
                    .sortBy(sortBy)
                    .build();
            AssetSearchCriteria assetSearchCriteria = AssetSearchCriteria
                    .builder()
                    .assetName(assetName)
                    .assetTypeId(assetTypeId)
                    .assetStatusId(assetStatusId)
                    .engravedNumber(engravedNumber)
                    .assignedToId(assignedToId)
                    .commodityId(commodityId)
                    .disposed(disposed)
                    .model(model)
                    .startDate(startDate)
                    .endDate(endDate)
                    .assignedTo(assignedTo)
                    .location(location)
                    .build();

            // "Due for disposal" is a derived state (depends on the receipt date and the category's
            // useful life), so it can't be expressed as a DB filter. Scan the matching (non-disposed)
            // assets, compute each one's disposal status, keep the ones that have reached their useful
            // life, and paginate the result in memory.
            if (Boolean.TRUE.equals(dueForDisposal)) {
                AssetPage scanPage = AssetPage.builder()
                        .pageSize(DISPOSAL_SCAN_LIMIT)
                        .sortDirection(determineSortDirection(sortDirection))
                        .pageNumber(0)
                        .sortBy(sortBy)
                        .build();
                Page<Asset> scan = assetSearchDao.findAllWithFilters(scanPage, assetSearchCriteria);
                List<AssetDAO> ready = mapToDTOList(scan.getContent()).stream()
                        .filter(dao -> "Ready for disposal".equals(dao.getDisposalStatus()))
                        .collect(Collectors.toList());

                int from = Math.min(pageNumber * pageSize, ready.size());
                int to = Math.min(from + pageSize, ready.size());
                return new PageImpl<>(ready.subList(from, to), PageRequest.of(pageNumber, pageSize), ready.size());
            }

            Page<Asset> assetPageResult = assetSearchDao.findAllWithFilters(assetPage, assetSearchCriteria);
            List<AssetDAO> dtoList = mapToDTOList(assetPageResult.getContent());
            return new PageImpl<>(dtoList, assetPageResult.getPageable(), assetPageResult.getTotalElements());
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public Asset createAsset(AssetDTO request) throws NotFoundException {
        Branch branch = (request.getBranch() != null) ? branchService.findBranch(request.getBranch()) : null;
        User assignedTo = (request.getAssignedTo() != null) ? userService.findUser(request.getAssignedTo()) : null;
        Status status = (request.getAssetStatus() != null) ? statusService.findOneStatus(request.getAssetStatus()) : null;
        AssetType assetType = (request.getAssetType() != null) ? assetTypeService.findOneAssetType(request.getAssetType()) : null;
        Supplier supplier = (request.getSupplier() != null) ? supplierService.findSupplier(request.getSupplier()) : null;
        Commodity commodity = commodityService.findCommodity(request.getCategory());

        Stock stock = findStockByLPONumber(request.getLpoNumber());
        assert commodity != null;
        checkIfStockContainsCommodityNotYetRegistered(stock, commodity);

        Asset asset = Asset.builder()
                .assetDepreciationRate(request.getAssetDepreciationRate())
                .assetStatus(status)
                .supplier(supplier)
                .costOfTheAsset(stripGroupingCommas(request.getCostOfTheAsset()))
                .assetName(request.getAssetName())
                .dateReceipt(request.getDateReceipt())
                .assetType(assetType)
                .assignedTo(assignedTo)
                .hardDiskSize(request.getHardDiskSize())
                .image(request.getImage())
                .hostname(request.getHostname())
                .make(request.getMake())
                .engravedNumber(request.getEngravedNumber())
                .interfaceType(request.getInterfaceType())
                .model(request.getModel())
                .ipAddress(request.getIpAddress())
                .macAddress(request.getMacAddress())
                .ram(request.getRam())
                .netValueB(request.getNetValueB())
                .purchaseCost(stripGroupingCommas(request.getPurchaseCost()))
                .serialNumber(request.getSerialNumber())
                .unitOfMeasure(request.getUnitOfMeasure())
                .cpuSpeed(request.getCpuSpeed())
                .detailNetBookValue(request.getDetailNetBookValue())
                .description(request.getDescription())
                .branch(branch)
                .stock(stock)
                .commodity(commodity)
                .temporaryPool(Boolean.TRUE.equals(request.getTemporaryPool()))
                .build();
        return assetRepository.save(asset);
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    private Stock findStockByLPONumber(String lpoNumber) throws
            NotFoundException {
        return stockRepository.findByLpoNumber(lpoNumber).orElseThrow(
                () -> new NotFoundException(String.format("Stock with LPO Number: %s is not found", lpoNumber))
        );
    }

    private void checkIfStockContainsCommodityNotYetRegistered(Stock stock, Commodity commodity)
            throws NotFoundException {

        StockCommodity stockCommodity = stockCommodityRepository.findByStockIdAndCommodityId(stock.getId(), commodity.getId())
                .orElseThrow(() -> new NotFoundException(
                        String.format("Commodity '%s' is not associated with the given stock (LPO: %s)",
                                commodity.getName(), stock.getLpoNumber())
                ));


        int availableQuantity = stockCommodity.getDeliveredQuantity() - stockCommodity.getRegisteredNumber();

        if (availableQuantity <= 0) {
            throw new IllegalArgumentException(
                    "Insufficient quantity for commodity: "
                            + commodity.getName()
                            + " in stock with LPO: "
                            + stock.getLpoNumber()
            );
        }

        stockCommodity.setRegisteredNumber(stockCommodity.getRegisteredNumber() + 1);
        stockCommodityRepository.save(stockCommodity);
    }

    public Asset findOne(Long id) throws NotFoundException {
        return assetRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("Asset with id %s is not found", id))
        );
    }

    @Transactional
    public AssetDAO fetchSingleAsset(Long assetId) throws NotFoundException {
        Asset asset = findOne(assetId);
        return buildAssetDao(asset);
    }

    @Transactional
    public AssetDAO updateAsset(AssetDTO request, Long id) throws NotFoundException {
        Branch branch = (request.getBranch() != null) ? branchService.findBranch(request.getBranch()) : null;
        User assignedTo = (request.getAssignedTo() != null) ? userService.findUser(request.getAssignedTo()) : null;
        Status status = (request.getAssetStatus() != null) ? statusService.findOneStatus(request.getAssetStatus()) : null;
        AssetType assetType = (request.getAssetType() != null) ? assetTypeService.findOneAssetType(request.getAssetType()) : null;
        Supplier supplier = (request.getSupplier() != null) ? supplierService.findSupplier(request.getSupplier()) : null;

        Asset asset = findOne(id);

        User oldAssignedTo = asset.getAssignedTo();
        Status oldStatus = asset.getAssetStatus();

        // Set Stock to not be deleted if an asset in the stock is already updated.
        Stock stock = findStockByLPONumber(request.getLpoNumber());
        if (!stock.isCanNotBeDeleted()) {
            stock.setCanNotBeDeleted(true);
            stockRepository.save(stock);
        }

        /*
         * Check if the assigned user of the asset has changed.
         * This involves comparing the existing assigned user ID with the new assigned user ID.
         * The check is null-safe to handle cases where assigned users might be null.
         * If the assigned user has changed, we call the method to track the asset reassignment history,
         * passing the asset, the new assigned user, the old status (before reassignment),
         * and the status after reassignment (which defaults to the new status if provided, otherwise the old status).
         */

        if (!Objects.equals(
                oldAssignedTo != null ? oldAssignedTo.getId() : null,
                assignedTo != null ? assignedTo.getId() : null
        )) {
            Status statusAfter = (status != null) ? status : oldStatus;
            trackAssetAssignmentHistory(asset, assignedTo, oldStatus, statusAfter);
        }

        asset.setAssetDepreciationRate(request.getAssetDepreciationRate());
        asset.setAssetStatus(status);
        asset.setSupplier(supplier);
        asset.setCostOfTheAsset(stripGroupingCommas(request.getCostOfTheAsset()));
        asset.setAssetName(request.getAssetName());
        asset.setDateReceipt(request.getDateReceipt());
        asset.setAssetType(assetType);
        asset.setAssignedTo(assignedTo);
        asset.setHardDiskSize(request.getHardDiskSize());
        asset.setImage(request.getImage());
        asset.setHostname(request.getHostname());
        asset.setMake(request.getMake());
        asset.setEngravedNumber(request.getEngravedNumber());
        asset.setInterfaceType(request.getInterfaceType());
        asset.setModel(request.getModel());
        asset.setIpAddress(request.getIpAddress());
        asset.setMacAddress(request.getMacAddress());
        asset.setRam(request.getRam());
        asset.setNetValueB(request.getNetValueB());
        asset.setPurchaseCost(stripGroupingCommas(request.getPurchaseCost()));
        asset.setSerialNumber(request.getSerialNumber());
        asset.setUnitOfMeasure(request.getUnitOfMeasure());
        asset.setCpuSpeed(request.getCpuSpeed());
        asset.setDetailNetBookValue(request.getDetailNetBookValue());
        asset.setDescription(request.getDescription());
        asset.setBranch(branch);
        if (request.getTemporaryPool() != null) {
            asset.setTemporaryPool(request.getTemporaryPool());
        }
        return buildAssetDao(assetRepository.save(asset));
    }

    /**
     * Tracks the reassignment history of an asset whenever its assigned user changes.
     * <p>
     * This method performs the following steps:
     * - Finds the currently active assignment history record for the given asset (where endDate is null).
     * - If such a record exists, it sets its endDate to the current time and updates its statusAfter field.
     * - Creates a new assignment history record representing the new assignment with the current time as startDate,
     * along with the status before and after the assignment.
     *
     * @param asset        the asset being reassigned
     * @param newUser      the new user to whom the asset is assigned
     * @param statusBefore the status of the asset before reassignment
     * @param statusAfter  the status of the asset after reassignment
     */
    @Transactional
    public void trackAssetAssignmentHistory(Asset asset, User newUser, Status statusBefore, Status statusAfter) {
        AssetAssignmentHistory activeHistory = assetAssignmentHistoryRepository
                .findActiveByAssetId(asset.getId());

        LocalDateTime now = LocalDateTime.now();

        if (activeHistory != null) {
            activeHistory.setEndDate(now);
            activeHistory.setStatusAfter(statusBefore);
            assetAssignmentHistoryRepository.save(activeHistory);
        }

        AssetAssignmentHistory newHistory = AssetAssignmentHistory.builder()
                .asset(asset)
                .user(newUser)
                .startDate(now)
                .statusBefore(statusBefore)
                .statusAfter(statusAfter)
                .build();

        assetAssignmentHistoryRepository.save(newHistory);
    }

    @Transactional
    public List<AssetTypeSummaryView> listDashboardAssetReport() {
        return assetRepository.getAssetTypeSummary();
    }


    private List<AssetDAO> mapToDTOList(List<Asset> assets) {
        return assets.stream().map(this::buildAssetDao).toList();
    }

    /** Derived depreciation values returned to the client (display-only / read-only on the form). */
    private record DepreciationValues(String grossValue, String currentNetBookValue,
                                      String accumulatedDepreciation, String annualDepreciation,
                                      String monthlyDepreciation, String rate, String disposalStatus) {}

    /**
     * Derives an asset's depreciation using the <b>reducing-balance</b> method.
     * Each year the charge is {@code openingNBV * rate}; the monthly figure is that year's charge ÷ 12.
     * <ul>
     *   <li>Net Value = gross (purchase) cost basis</li>
     *   <li>Detail Net Book Value = current depreciated value (prorated by whole months since receipt)</li>
     *   <li>Accumulated Depreciation = cost − current NBV</li>
     *   <li>Annual / Monthly Depreciation = the charge at the current value</li>
     *   <li>Disposal Status = "Ready for disposal" once the asset's age reaches the category's useful life</li>
     * </ul>
     * Disposal is purely age-based (useful life in months) and is evaluated independently of the
     * depreciation figures. Currency values carry thousands separators; the rate carries a percent sign.
     * When the category has no rate or the asset has no cost basis, stored values are returned unchanged.
     */
    private DepreciationValues computeDepreciation(Asset asset) {
        Double ratePct = (asset.getAssetType() != null) ? asset.getAssetType().getDepreciationRate() : null;
        Integer usefulLifeMonths = (asset.getAssetType() != null) ? asset.getAssetType().getUsefulLifeMonths() : null;
        Double cost = parseMoney(asset.getCostOfTheAsset());
        long elapsedMonths = monthsSinceReceipt(asset.getDateReceipt());

        // Disposal is age-based and independent of the depreciation math: an asset is ready once
        // it has been held for at least its category's useful life.
        String disposalStatus = (usefulLifeMonths != null && usefulLifeMonths > 0)
                ? (elapsedMonths >= usefulLifeMonths ? "Ready for disposal" : "In service")
                : null;

        if (ratePct == null || cost == null) {
            return new DepreciationValues(
                    asset.getNetValueB(),
                    asset.getDetailNetBookValue(),
                    null, null, null,
                    ratePct != null ? formatNumber(ratePct) + "%" : asset.getAssetDepreciationRate(),
                    disposalStatus);
        }

        double rate = ratePct / 100.0;

        // Reducing balance: recalc the charge each year on the opening NBV and accrue monthly.
        double nbv = cost;
        long remaining = elapsedMonths;
        while (remaining > 0 && nbv > 0) {
            double monthly = (nbv * rate) / 12.0;
            long monthsThisYear = Math.min(12, remaining);
            nbv -= monthly * monthsThisYear;
            remaining -= monthsThisYear;
        }

        double currentNbv = Math.max(0.0, nbv);
        double accumulated = cost - currentNbv;

        // Forward-looking charge at the current value.
        double annualNow = currentNbv * rate;
        double monthlyNow = annualNow / 12.0;

        return new DepreciationValues(
                formatCurrency(cost),
                formatCurrency(currentNbv),
                formatCurrency(accumulated),
                formatCurrency(annualNow),
                formatCurrency(monthlyNow),
                formatNumber(ratePct) + "%",
                disposalStatus);
    }

    private Double parseMoney(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.replaceAll("[,\\s]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Whole months between the receipt date and today; 0 when missing/unparseable/future. */
    private long monthsSinceReceipt(String dateReceipt) {
        if (dateReceipt == null || dateReceipt.isBlank()) return 0L;
        java.time.LocalDate date = parseDateFlexible(dateReceipt.trim());
        if (date == null) return 0L;
        long months = java.time.temporal.ChronoUnit.MONTHS.between(date, java.time.LocalDate.now());
        return Math.max(0L, months);
    }

    private java.time.LocalDate parseDateFlexible(String value) {
        try { return java.time.LocalDateTime.parse(value).toLocalDate(); } catch (Exception ignored) { }
        try { return java.time.OffsetDateTime.parse(value).toLocalDate(); } catch (Exception ignored) { }
        try { return java.time.LocalDate.parse(value); } catch (Exception ignored) { }
        int t = value.indexOf('T');
        if (t > 0) {
            try { return java.time.LocalDate.parse(value.substring(0, t)); } catch (Exception ignored) { }
        }
        return null;
    }

    /** Currency display with thousands separators and 2 decimals, e.g. 1234567.5 → "1,234,567.50". */
    private String formatCurrency(double value) {
        return String.format(java.util.Locale.US, "%,.2f", value);
    }

    /** Formats a stored money string for display (thousands separators); returns it unchanged when not numeric. */
    private String formatMoneyString(String value) {
        Double parsed = parseMoney(value);
        return parsed != null ? formatCurrency(parsed) : value;
    }

    /** Strips grouping commas/spaces so money is persisted as a clean numeric string (display adds separators). */
    private String stripGroupingCommas(String value) {
        return value == null ? null : value.replaceAll("[,\\s]", "");
    }

    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    public AssetDAO buildAssetDao(Asset asset) {
        DepreciationValues dep = computeDepreciation(asset);
        return AssetDAO.builder()
                .id(asset.getId())
                .assetName(asset.getAssetName())
                .hostname(asset.getHostname())
                .detailNetBookValue(dep.currentNetBookValue())
                .engravedNumber(asset.getEngravedNumber())
                .dateReceipt(asset.getDateReceipt())
                .make(asset.getMake())
                .unitOfMeasure(asset.getUnitOfMeasure())
                .purchaseCost(formatMoneyString(asset.getPurchaseCost()))
                .costOfTheAsset(formatMoneyString(asset.getCostOfTheAsset()))
                .netValueB(dep.grossValue())
                .model(asset.getModel())
                .serialNumber(asset.getSerialNumber())
                .ram(asset.getRam())
                .cpuSpeed(asset.getCpuSpeed())
                .hardDiskSize(asset.getHardDiskSize())
                .macAddress(asset.getMacAddress())
                .ipAddress(asset.getIpAddress())
                .interfaceType(asset.getInterfaceType())
                .assetDepreciationRate(dep.rate())
                .accumulatedDepreciation(dep.accumulatedDepreciation())
                .annualDepreciation(dep.annualDepreciation())
                .monthlyDepreciation(dep.monthlyDepreciation())
                .disposalStatus(dep.disposalStatus())
                .description(asset.getDescription())
                .image(asset.getImage())
                .lastRepairedBy(asset.getLastRepairedBy())
                .temporaryPool(asset.isTemporaryPool())

                // Map nested references as simple DTOs or just their IDs and names to avoid recursion
                .supplier(asset.getSupplier() != null ?
                        Supplier.builder()
                                .id(asset.getSupplier().getId())
                                .name(asset.getSupplier().getName())
                                .address(asset.getSupplier().getAddress())
                                .telephone(asset.getSupplier().getTelephone())
                                .email(asset.getSupplier().getEmail())
                                .build() : null)
                .assignedTo(asset.getAssignedTo() != null ?
                        User.builder()
                                .availability(asset.getAssignedTo().getAvailability())
                                .branch(asset.getAssignedTo().getBranch())
                                .email(asset.getAssignedTo().getEmail())
                                .firstName(asset.getAssignedTo().getFirstName())
                                .lastName(asset.getAssignedTo().getLastName())
                                .otherName(asset.getAssignedTo().getOtherName())
                                .gender(asset.getAssignedTo().getGender())
                                .staffNumber(asset.getAssignedTo().getStaffNumber())
                                .title(asset.getAssignedTo().getTitle())
                                .department(asset.getAssignedTo().getDepartment())
                                .isEnabled(asset.getAssignedTo().isEnabled())
                                .isAccountNonLocked(asset.getAssignedTo().isAccountNonLocked())
                                .blocked(asset.getAssignedTo().isBlocked())
                                .build() : null)
                .branch(asset.getBranch() != null ?
                        Branch.builder()
                                .id(asset.getBranch().getId())
                                .district(asset.getBranch().getDistrict())
                                .branchManager(asset.getBranch().getBranchManager())
                                .branchOperationsManager(asset.getBranch().getBranchOperationsManager())
                                .creditAdministrator(asset.getBranch().getCreditAdministrator())
                                .relationshipManager(asset.getBranch().getRelationshipManager())
                                .name(asset.getBranch().getName())
                                .region(asset.getBranch().getRegion())
                                .telephone(asset.getBranch().getTelephone())
                                .email(asset.getBranch().getEmail())
                                .build() : null)
                .assetStatus(asset.getAssetStatus() != null ?
                        Status.builder()
                                .id(asset.getAssetStatus().getId())
                                .code(asset.getAssetStatus().getCode())
                                .name(asset.getAssetStatus().getName())
                                .description(asset.getAssetStatus().getDescription())
                                .status(asset.getAssetStatus().getStatus())
                                .build() : null)
                .assetType(asset.getAssetType() != null ?
                        AssetType.builder()
                                .id(asset.getAssetType().getId())
                                .name(asset.getAssetType().getName())
                                .description(asset.getAssetType().getDescription())
                                // Repair routing config rides along so the repair-transfer UI can
                                // default the destination and enforce the EXTERNAL consultant rule.
                                .repairable(asset.getAssetType().isRepairable())
                                .repairDestination(asset.getAssetType().getRepairDestination())
                                .build() : null)
                .commodity(asset.getCommodity() != null ?
                        Commodity.builder()
                                .id(asset.getCommodity().getId())
                                .name(asset.getCommodity().getName())
                                .groupName(asset.getCommodity().getGroupName())
                                .build() : null)
                .stock(asset.getStock() != null ?
                        Stock.builder()
                                .id(asset.getStock().getId())
                                .referenceNumber(asset.getStock().getReferenceNumber())
                                .name(asset.getStock().getName())
                                .totalCost(asset.getStock().getTotalCost())
                                .balanceCost(asset.getStock().getBalanceCost())
                                .lpoNumber(asset.getStock().getLpoNumber())
                                .grnNumber(asset.getStock().getGrnNumber())
                                .build() : null)
                .createDate(asset.getCreateDate())
                .lastModified(asset.getLastModified())
                .createdBy(asset.getCreatedBy())
                .lastModifiedBy(asset.getLastModifiedBy())
                .build();
    }

    public List<AssetTypeStatsProjection> getAssetTypeStatistics() {
        Long branchId = currentUserService.getCurrentUser().getBranch().getId();
        return assetRepository.getAssetStatsByType(branchId);
    }

    public List<AssetGroupDTO> getAssetsGroupedByType() {
        Long currentUserId = currentUserService.getCurrentUser().getId();
        List<Asset> assets = assetRepository.findAllByAssignedToIdWithTypeAndStatus(currentUserId);

        Map<String, List<Asset>> grouped = assets.stream()
                .collect(Collectors.groupingBy(a -> a.getAssetType().getName()));

        return grouped.entrySet().stream()
                .map(entry -> new AssetGroupDTO(
                        entry.getKey(),
                        (long) entry.getValue().size(),
                        entry.getValue().stream()
                                .map(a -> new AssetGroupDTO.AssetDTO(
                                        a.getId(),
                                        a.getAssetName(),
                                        a.getEngravedNumber(),
                                        a.getAssetStatus() != null ? a.getAssetStatus().getName() : null,
                                        a.getSerialNumber()
                                ))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());
    }

    public Asset findAssetByTagName(String tagName) throws NotFoundException {
        return assetRepository.findByEngravedNumber(tagName)
                .orElseThrow(
                        () -> new NotFoundException(String
                                .format("Asset with Tag Number: %s is not found", tagName))
                );
    }

    @Transactional
    public AssetDAO disposeAsset(Long assetID) throws NotFoundException {
        Asset asset = findOne(assetID);
        asset.setDisposed(true);
        asset.setDisposalDate(LocalDateTime.now());
        Asset data = assetRepository.save(asset);
        return buildAssetDao(data);
    }

    @Transactional
    public AssetDAO reassignAsset(Long assetID, ReassignAsset reassignAsset)
            throws NotFoundException {
        Asset asset = findOne(assetID);

        Long previousUserId = null;
        if (asset.getAssignedTo() != null) {
            previousUserId = asset.getAssignedTo().getId();
        }

        User user = userService.findUser(reassignAsset.assignedTo());
        asset.setAssignedTo(user);
        asset.setBranch(user.getBranch());

        AssetDAO data = buildAssetDao(assetRepository.save(asset));
        updateAssetAssignmentHistory(asset, user);
        if (previousUserId != null) {
            addEndDateForPreviousAssignedTo(assetID, previousUserId);
        }
        notifyUserAfterAssignment(user, asset);
        return data;
    }

    public AssetDAO uploadAssetImage(MultipartFile file, Long assetID)
            throws IOException, NotFoundException {
        Asset asset = findOne(assetID);
        String filePath = null;

        if (file != null && !file.isEmpty()) {
            try {
                filePath = fileUpload.uploadFileToLocalDirectory(file);
            } catch (IOException ex) {
                throw new IOException("Failed to upload file: " + ex.getMessage(), ex);
            }
        }
        asset.setImage(filePath);
        return buildAssetDao(assetRepository.save(asset));
    }

    public AssetDAO removeAssetImage(Long assetID)
            throws NotFoundException {
        Asset asset = findOne(assetID);
        asset.setImage(null);
        return buildAssetDao(assetRepository.save(asset));
    }

    public AssetDAO sendAssetToStore(Long assetID) throws NotFoundException {
        Asset asset = findOne(assetID);
        Status status = statusService.findByStatusCode("sentToStore"); // Asset sent to store
        asset.setAssetStatus(status);
        asset.setAssignedTo(null);
        return buildAssetDao(assetRepository.save(asset));
    }

    /**
     * Flips whether this asset is pool stock for temporary replacement loans (§15). Only meaningful
     * for an asset currently unassigned and sitting in a store — flipping it on an operational,
     * assigned asset doesn't relocate or unassign it, it just marks intent for the next repair cycle.
     */
    @Transactional
    public AssetDAO toggleTemporaryPool(Long assetID) throws NotFoundException {
        Asset asset = findOne(assetID);
        asset.setTemporaryPool(!asset.isTemporaryPool());
        return buildAssetDao(assetRepository.save(asset));
    }

    public Map<String, Object> bulkInsertAsset(List<BulkAssetDTO> assetList, Long assetTypeID)
            throws NotFoundException {
        Branch defaultBranch = branchService.findBranch(1L); // Default to Head Office
        Status status = statusService.findByStatusCode("requireUpdate"); // Requires detail completion
        AssetType assetType = assetTypeService.findOneAssetType(assetTypeID);

        Map<String, Object> data = new HashMap<>();

        try {
            for (BulkAssetDTO dto : assetList) {
                Branch branch = branchService.findBranchByName(dto.getLocation());

                if (branch == null) {
                    branch = defaultBranch;
                }
                LocalDate dateReceipt = LocalDate.of(1899, 12, 30).plusDays(dto.getDateOfPmVerification());
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                String dateReceiptString = dateReceipt.format(formatter);

                Asset asset = Asset.builder()
                        .assetName(dto.getProductName())
                        .engravedNumber(dto.getAssetTag())
                        .serialNumber(dto.getAssetSerialNo())
                        .model(dto.getModel())
                        .branch(branch)
                        .assetStatus(status)
                        .disposed(false)
                        .assetType(assetType)
                        .dateReceipt(dateReceiptString)
                        .build();
                assetRepository.save(asset);
            }

            data.put("message", "Assets created");
            data.put("success", true);
        } catch (Exception e) {
            data.put("message", e.getMessage());
            data.put("success", false);
        }
        return data;
    }

    private void updateAssetAssignmentHistory(
            Asset asset,
            User user
    ) {
        AssetAssignmentHistory assetAssignmentHistory = AssetAssignmentHistory
                .builder()
                .asset(asset)
                .endDate(null)
                .startDate(LocalDateTime.now())
                .statusBefore(asset.getAssetStatus())
                .statusAfter(null)
                .user(user)
                .build();

        assetAssignmentHistoryRepository.save(assetAssignmentHistory);
    }

    private void addEndDateForPreviousAssignedTo(Long assetId, Long userId) {
        AssetAssignmentHistory record = assetAssignmentHistoryRepository
                .findByUserIdAndAssetId(userId, assetId).orElse(null);

        assert record != null;

        record.setEndDate(LocalDateTime.now());
        assetAssignmentHistoryRepository.save(record);
    }

    private void notifyUserAfterAssignment(User user, Asset asset) {
        String assetName = asset.getAssetName() + " " + "(" + asset.getEngravedNumber() + ")";
        email.sendEmailVerification(
                user.getEmail(),
                "Account Verification",
                user.getFirstName(),
                "Notification",
                "Asset Notification",
                assetName,
                "notification",
                null,
                null,
                null,
                null,
                null
        );
    }

}
