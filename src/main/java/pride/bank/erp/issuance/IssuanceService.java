package pride.bank.erp.issuance;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistory;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.issuedCommodity.IssuedCommodity;
import pride.bank.erp.issuedCommodity.IssuedCommodityRepository;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.request.RequestService;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.stock.CommodityWithDetailsDao;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IssuanceService {
    private final UserService userService;
    private final IssuanceRepository issuanceRepository;
    private final StatusService statusService;
    private final CommodityService commodityService;
    private final IssuedCommodityRepository issuedCommodityRepository;
    private final StoreService storeService;
    private final AssetService assetService;
    private final AssetRepository assetRepository;
    private final RequestService requestService;
    private final RequestRepository requestRepository;
    private final AssetAssignmentHistoryRepository assetAssignmentHistoryRepository;

    @Transactional
    public Issuance issueCommodities(IssuanceDTO request)
            throws NotFoundException {
        User requester = userService.findUser(request.getRequester());
        Status status = statusService.findOneStatus(5L);

        updateAssetAssignedToStatusAndStatus(request.getAssets(), requester);

        /*
            TO DO
            Send Email to Requester to acknowledge Asset Reception!!
            Change th status of the request to issue!
            NB: Probably add a tab for issued Request in the Tab.
         */

        Issuance issuance = Issuance
                .builder()
                .status(status)
                .requester(requester)
                .comment(request.getComment())
                .build();
        Issuance savedIssuance = issuanceRepository.save(issuance);
        addIssuedCommodities(savedIssuance, request.getCommodities());
        updateBranchStockLevel(request.getCommodities());
        updateRequestStatus(request);

        /*
         * Update Asset Assignment History
         * Add Active to working or damaged.
         */

        return savedIssuance;
    }


    @Transactional
    private void addIssuedCommodities(Issuance issuance, List<IssuanceCommodityDTO> commodities)
            throws NotFoundException {
        for (IssuanceCommodityDTO dto : commodities) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
            IssuedCommodity stockCommodity = IssuedCommodity
                    .builder()
                    .commodity(commodity)
                    .quantity(dto.getQuantity())
                    .issuance(issuance)
                    .build();
            issuedCommodityRepository.save(stockCommodity);
        }
    }

    @Transactional
    private void updateBranchStockLevel(List<IssuanceCommodityDTO> issuanceCommodityDTOS)
            throws NotFoundException {
        List<CommodityWithDetailsDao> commodities = commodityWithDetailsDao(issuanceCommodityDTOS);
        /*
            TO DO...
            add commodities to branch store only if the issuance is from head office to another.
            Otherwise, just reduce the quantity from the head office store.

            NB: implement the part where an item is issued to branch and then to an individual.
            NB: Branch stores are only updated if the asset is stationary, IT equipment are issued to the users directly.
            When a branch user makes a request, the request will be approved by supervisor and will be visible to BOM.
            BOM will make a request to head office, this request will be approved by BOM supervisor.
            Once admin receives this request, it will be approved and store updated (stationery items).
            Then BOM will issue request of the officer.
            NB: Email the BOM after issuance or the requester after issuing a request
         */

        storeService.addCommoditiesToStore(commodities, 2L);
        storeService.reduceCommoditiesFromStore(commodities, 1L);
    }


    private List<CommodityWithDetailsDao> commodityWithDetailsDao(List<IssuanceCommodityDTO> issuanceCommodityDTOS)
            throws NotFoundException {
        List<CommodityWithDetailsDao> commoditiesWithDetails = new ArrayList<>();

        for (IssuanceCommodityDTO dto : issuanceCommodityDTOS) {
            Commodity commodity = commodityService.findCommodity(dto.getCommodityId());
            CommodityWithDetailsDao details = CommodityWithDetailsDao
                    .builder()
                    .commodity(commodity)
                    .deliveredQuantity(dto.getQuantity())
                    .build();
            commoditiesWithDetails.add(details);
        }

        return commoditiesWithDetails;
    }

    public IssuedCommodity getLastIssuedCommodity(Long commodityId) {
        List<IssuedCommodity> result = issuedCommodityRepository.findLastIssuedCommodityByCommodityId(
                commodityId,
                PageRequest.of(0, 1)
        );
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    /**
     * Updates the assignment and status of a list of assets to a specified user.
     * <p>
     * The method performs the following:
     * - Retrieves the "assigned" status (ID 4) from the status service.
     * - For each asset in the list, it fetches the current asset data.
     * - Checks if the asset is already assigned to the specified user with the assigned status; if so, it skips updating that asset.
     * - If the asset's assigned user differs from the specified user, it calls the method to track the asset assignment history.
     * - Updates the asset's assigned user and status to the new values.
     * - Saves the updated asset back to the repository.
     *
     * @param assets the list of assets to be updated
     * @param user   the user to whom the assets will be assigned
     * @throws NotFoundException if the assigned status is not found or if any asset does not exist
     */
    @Transactional
    private void updateAssetAssignedToStatusAndStatus(List<IssueAssetsDTO> assets, User user) throws NotFoundException {
        Status assignedStatus = statusService.findOneStatus(4L);
        if (assignedStatus == null) {
            throw new NotFoundException("Status with ID 4 not found.");
        }

        for (IssueAssetsDTO asst : assets) {
            Asset asset = assetService.findOne(asst.getId());

            User oldUser = asset.getAssignedTo();
            Status oldStatus = asset.getAssetStatus();

            if (oldUser != null && oldUser.getId().equals(user.getId()) && oldStatus != null && oldStatus.getId() == 4) {
                continue;
            }

            if (oldUser == null || !oldUser.getId().equals(user.getId())) {
                trackAssetAssignmentHistory(asset, user, oldStatus, assignedStatus);
            }

            asset.setAssignedTo(user);
            asset.setAssetStatus(assignedStatus);

            assetRepository.save(asset);
        }
    }

    @Transactional
    private void updateRequestStatus(IssuanceDTO request)
            throws NotFoundException {
        Request requestDetails = requestService.findRequest(request.getRequestId());
        Status status = statusService.findOneStatus(5L); // Get The Issued Status and assign to Request
        requestDetails.setStatus(status);
        requestRepository.save(requestDetails);
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
    private void trackAssetAssignmentHistory(Asset asset, User newUser, Status statusBefore, Status statusAfter) {
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

    public Issuance findOne(Long id)
            throws NotFoundException {
        return issuanceRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("Issuance with Id %s is not found", id))
        );
    }

}

