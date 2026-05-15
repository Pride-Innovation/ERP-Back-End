/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.region;


import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.util.Objects;
import java.util.Optional;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;


@Service
@RequiredArgsConstructor
public class RegionService {
    private final RegionRepository regionRepository;
    private final RegionSearchDao regionSearchDao;

    public Page<Region> fetchAllRegions(RegionQueryParams params) {
        RegionPage regionPage = RegionPage.builder()
                .pageSize(params.getPageSize())
                .sortDirection(determineSortDirection(params.getSortDirection()))
                .sortBy(params.getSortBy())
                .pageNumber(params.getPageNumber())
                .build();

        RegionSearchCriteria regionSearchCriteria = RegionSearchCriteria
                .builder()
                .name(params.getName())
                .build();
        return regionSearchDao.findAllWithFilters(regionPage, regionSearchCriteria);
    }

    @Transactional
    public Region createRegion(RegionRequestDTO request) {
        ensureNoDuplicateRegionName(request.getName(), null);
        Region region = Region.builder()
                .name(request.getName())
                .build();
        return regionRepository.save(region);
    }

    @Transactional
    public Region updateRegion(RegionRequestDTO request, Long id) throws NotFoundException {
        Region region = findRegion(id);
        ensureNoDuplicateRegionName(request.getName(), id);
        region.setName(request.getName());
        return regionRepository.save(region);
    }


    public Region findRegion(Long id) throws NotFoundException {
        return regionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Region with ID " + id + " not found"));
    }

    @Transactional
    public DeleteResponse deleteRegion(Long id) throws NotFoundException {
        if (!regionRepository.existsById(id)) {
            throw new NotFoundException("Region with ID " + id + " not found");
        }
        regionRepository.deleteById(id);
        return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    private void ensureNoDuplicateRegionName(String name, Long excludeId) {
        Optional<Region> existing = regionRepository.findByNameIgnoreCase(name);
        if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
            throw new IllegalArgumentException("A region with the name '" + name + "' already exists");
        }
    }
}
