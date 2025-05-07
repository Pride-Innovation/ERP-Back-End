package pride.bank.erp.region;


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
public class RegionService {
    private final RegionRepository regionRepository;
    private final RegionSearchDao regionSearchDao;

    public Page<Region> fetchAllRegions(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String name
    ) {
        try {
            RegionPage regionPage = RegionPage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .sortBy(sortBy)
                    .pageNumber(pageNumber)
                    .build();

            RegionSearchCriteria regionSearchCriteria = RegionSearchCriteria
                    .builder()
                    .name(name)
                    .build();
            return regionSearchDao.findAllWithFilters(regionPage, regionSearchCriteria);
        } catch (Exception e) {
            return null;
        }
    }

    public Region createRegion(Region request) {
        Region region = Region.builder()
                .name(request.getName())
                .build();
        return regionRepository.save(region);
    }

    public Region updateRegion(Region request, Long id) throws NotFoundException {
        Region region = findRegion(id);
        region.setName(request.getName());
        return regionRepository.save(region);
    }


    public Region findRegion(Long id) throws NotFoundException {
        return regionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Region with ID " + id + " not found"));
    }

    public DeleteResponse deleteRegion(Long id) {
        if (regionRepository.existsById(id)) {
            regionRepository.deleteById(id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
