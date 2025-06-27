package pride.bank.erp.issuance;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.asset.AssetService;
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

    @Transactional
    private void updateAssetAssignedToStatusAndStatus(List<IssueAssetsDTO> assets, User user)
            throws NotFoundException {

        // Step 1: Validate that none of the assets are already assigned
        for (IssueAssetsDTO asst : assets) {
            Asset asset = assetService.findOne(asst.getId());

            if (asset.getAssignedTo() != null && asset.getAssetStatus() != null && asset.getAssetStatus().getId() == 4) {
                throw new IllegalStateException(String.format(
                        "Asset with Engraved No '%s' is already assigned to %s %s.",
                        asset.getEngravedNumber(),
                        asset.getAssignedTo().getFirstName(),
                        asset.getAssignedTo().getLastName()
                ));
            }
        }

        // Step 2: If all assets pass, fetch the assigned status once
        Status assignedStatus = statusService.findOneStatus(4L);
        if (assignedStatus == null) {
            throw new NotFoundException("Status with ID 4 not found.");
        }

        // Step 3: Proceed to update each asset
        for (IssueAssetsDTO asst : assets) {
            Asset asset = assetService.findOne(asst.getId());
            asset.setAssignedTo(user);
//            asset.setBranch(user.getBranch()); // Optional: Only if needed
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

}

