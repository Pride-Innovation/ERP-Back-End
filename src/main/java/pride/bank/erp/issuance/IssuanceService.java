package pride.bank.erp.issuance;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.issuedCommodity.IssuedCommodity;
import pride.bank.erp.issuedCommodity.IssuedCommodityRepository;
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

    @Transactional
    public Issuance issueCommodities(IssuanceDTO request)
            throws NotFoundException {
        User requester = userService.findUser(request.getRequester());
        Status status = statusService.findOneStatus(5L);

        Issuance issuance = Issuance
                .builder()
                .status(status)
                .requester(requester)
                .comment(request.getComment())
                .build();
        Issuance savedIssuance = issuanceRepository.save(issuance);
        addIssuedCommodities(savedIssuance, request.getCommodities());
        updateBranchStockLevel(request.getCommodities());
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
}
