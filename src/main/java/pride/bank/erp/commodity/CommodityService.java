package pride.bank.erp.commodity;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.util.Objects;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;


@Service
@RequiredArgsConstructor
public class CommodityService {
    private final CommodityRepository commodityRepository;
    private final CommoditySearchDao commoditySearchDao;

    public Page<Commodity> fetchAllCommodities(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String name
    ) {
        try {
            CommodityPage commodityPage = CommodityPage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .sortBy(sortBy)
                    .pageNumber(pageNumber)
                    .build();

            CommoditySearchCriteria commoditySearchCriteria = CommoditySearchCriteria
                    .builder()
                    .name(name)
                    .build();
            return commoditySearchDao.findAllWithFilters(commodityPage, commoditySearchCriteria);
        } catch (Exception e) {
            return null;
        }
    }

    public Commodity createCommodity(Commodity request) {
        Commodity commodity = Commodity.builder()
                .name(request.getName())
                .groupName(request.getGroupName())
                .build();
        return commodityRepository.save(commodity);
    }

    public Commodity updateCommodity(Commodity request, Long id) throws NotFoundException {
        Commodity commodity = findCommodity(id);

        commodity.setName(request.getName());
        commodity.setGroupName(request.getGroupName());
        return commodityRepository.save(commodity);
    }

    public Commodity findCommodity(Long id) throws NotFoundException {
        return commodityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Commodity with ID " + id + " not found"));
    }

    public DeleteResponse deleteCommodity(Long id) {
        if (commodityRepository.existsById(id)) {
            commodityRepository.deleteById(id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
