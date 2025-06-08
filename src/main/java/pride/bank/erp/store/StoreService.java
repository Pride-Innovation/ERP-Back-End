package pride.bank.erp.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchService;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.stock.CommodityWithDetailsDao;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {
    private final StoreRepository storeRepository;
    private final BranchService branchService;

    /**
     * Adds or updates commodities in the store for a specific branch.
     * <p>
     * For each commodity in the input list:
     * - If the commodity already exists in the store for the branch, its quantity is increased.
     * - If the commodity does not exist in the store, a new record is created with the delivered quantity.
     *
     * @param commodities a list of CommodityWithDetailsDao objects containing commodity and delivery info
     * @param branchID    the ID of the branch where the commodities should be stored
     * @throws NotFoundException if the branch with the given ID does not exist
     */

    @Transactional
    public void addCommoditiesToStore(List<CommodityWithDetailsDao> commodities, Long branchID)
            throws NotFoundException {
        Branch branch = branchService.findBranch(branchID);

        for (CommodityWithDetailsDao dao : commodities) {
            Commodity commodity = dao.getCommodity();

            Store store = storeRepository.findByBranchAndCommodity(branch, commodity)
                    .orElse(null);

            if (store != null) {
                store.setQuantity(store.getQuantity() + dao.getDeliveredQuantity());
            } else {
                store = Store.builder()
                        .branch(branch)
                        .commodity(commodity)
                        .quantity(dao.getDeliveredQuantity())
                        .build();
            }

            storeRepository.save(store);
        }
    }

    /**
     * Fetches all store entries for a given branch, including the commodity and branch details.
     *
     * @param branchId the ID of the branch to fetch store details for
     * @return list of Store entities with commodity and branch eagerly loaded
     * @throws NotFoundException if no store entries are found for the branch
     */
    @Transactional(readOnly = true)
    public List<Store> fetchStoreDetailsPerBranch(Long branchId)
            throws NotFoundException {

        List<Store> storeList = storeRepository.findAllByBranchIdWithCommodityAndBranch(branchId);

        if (storeList.isEmpty()) {
            throw new NotFoundException(
                    String.format("Store details for branch with id %s are not found", branchId)
            );
        }

        return storeList;
    }

    @Transactional
    public Page<Store> fetchStoreDetailsPerBranchPerAssetType(Long branchId, Long assetTypeId, Pageable pageable) {
        return storeRepository
                .findByBranchIdAndCommodity_AssetTypeId(branchId, assetTypeId, pageable);
    }
}
