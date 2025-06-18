package pride.bank.erp.asset;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeService;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchService;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.role.RolePage;
import pride.bank.erp.role.RoleSearchCriteria;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.supplier.SupplierService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserService;

import java.util.Objects;

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

    public Page<Asset> fetchAllAssets(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String assetName,
            Long assetTypeId
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
                    .build();
            return assetSearchDao.findAllWithFilters(assetPage, assetSearchCriteria);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public Asset createAsset(AssetDTO request) throws NotFoundException {
        Branch branch = null;
        User assignedTo = null;
        Status status = null;
        AssetType assetType = null;
        Supplier supplier = null;
        Commodity commodity = null;

        if (request.getBranch() != null) {
            branch = branchService.findBranch(request.getBranch());
        }

        if (request.getAssignedTo() != null) {
            assignedTo = userService.findUser(request.getAssignedTo());
        }

        if (request.getAssetStatus() != null) {
            status = statusService.findOneStatus(request.getAssetStatus());
        }

        if (request.getAssetType() != null) {
            assetType = assetTypeService.findOneAssetType(request.getAssetType());
        }

        if (request.getSupplier() != null) {
            supplier = supplierService.findSupplier(request.getSupplier());
        }

        if (request.getCategory() != null) {
            commodity = commodityService.findCommodity(request.getCategory());
        }

        /*
         * TO DO
         * find stock by LPO Number provided while creating the asset
         * find stock commodity using stock id and commodity id;
         * NB: Stock Commodity should have registered column which tracks the total number of assets registered from that which was stocked.
         * This number should always be reduced. compared to delivered quantity.
         */

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
                .commodity(commodity)
                .build();
        return assetRepository.save(asset);
    }


    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
