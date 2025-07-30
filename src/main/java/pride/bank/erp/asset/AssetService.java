/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistory;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeService;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchDAO;
import pride.bank.erp.branch.BranchService;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.request.CommodityWithQuantityDTO;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestDTO;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestReport.RequestReport;
import pride.bank.erp.requestReport.RequestReportDAO;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.stock.Stock;
import pride.bank.erp.stock.StockRepository;
import pride.bank.erp.stockCommodity.StockCommodity;
import pride.bank.erp.stockCommodity.StockCommodityRepository;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.supplier.SupplierDTO;
import pride.bank.erp.supplier.SupplierService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserDAO;
import pride.bank.erp.user.UserDTO;
import pride.bank.erp.user.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
            Long assignedToId
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
                    .build();

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
                .costOfTheAsset(request.getCostOfTheAsset())
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
                .purchaseCost(request.getPurchaseCost())
                .serialNumber(request.getSerialNumber())
                .unitOfMeasure(request.getUnitOfMeasure())
                .cpuSpeed(request.getCpuSpeed())
                .detailNetBookValue(request.getDetailNetBookValue())
                .description(request.getDescription())
                .branch(branch)
                .stock(stock)
                .commodity(commodity)
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
    public Asset updateAsset(AssetDTO request, Long id) throws NotFoundException {
        Branch branch = (request.getBranch() != null) ? branchService.findBranch(request.getBranch()) : null;
        User assignedTo = (request.getAssignedTo() != null) ? userService.findUser(request.getAssignedTo()) : null;
        Status status = (request.getAssetStatus() != null) ? statusService.findOneStatus(request.getAssetStatus()) : null;
        AssetType assetType = (request.getAssetType() != null) ? assetTypeService.findOneAssetType(request.getAssetType()) : null;
        Supplier supplier = (request.getSupplier() != null) ? supplierService.findSupplier(request.getSupplier()) : null;

        Asset asset = findOne(id);

        User oldAssignedTo = asset.getAssignedTo();
        Status oldStatus = asset.getAssetStatus();

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
        asset.setCostOfTheAsset(request.getCostOfTheAsset());
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
        asset.setPurchaseCost(request.getPurchaseCost());
        asset.setSerialNumber(request.getSerialNumber());
        asset.setUnitOfMeasure(request.getUnitOfMeasure());
        asset.setCpuSpeed(request.getCpuSpeed());
        asset.setDetailNetBookValue(request.getDetailNetBookValue());
        asset.setDescription(request.getDescription());
        asset.setBranch(branch);

        return assetRepository.save(asset);
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

    private AssetDAO buildAssetDao(Asset asset) {
        return AssetDAO.builder()
                .id(asset.getId())
                .assetName(asset.getAssetName())
                .hostname(asset.getHostname())
                .detailNetBookValue(asset.getDetailNetBookValue())
                .engravedNumber(asset.getEngravedNumber())
                .dateReceipt(asset.getDateReceipt())
                .make(asset.getMake())
                .unitOfMeasure(asset.getUnitOfMeasure())
                .purchaseCost(asset.getPurchaseCost())
                .costOfTheAsset(asset.getCostOfTheAsset())
                .netValueB(asset.getNetValueB())
                .model(asset.getModel())
                .serialNumber(asset.getSerialNumber())
                .ram(asset.getRam())
                .cpuSpeed(asset.getCpuSpeed())
                .hardDiskSize(asset.getHardDiskSize())
                .macAddress(asset.getMacAddress())
                .ipAddress(asset.getIpAddress())
                .interfaceType(asset.getInterfaceType())
                .assetDepreciationRate(asset.getAssetDepreciationRate())
                .description(asset.getDescription())
                .image(asset.getImage())

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

}
