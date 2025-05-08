/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.district;

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
public class DistrictService {
    private final DistrictRepository districtRepository;
    private final DistrictSearchDao districtSearchDao;

    public Page<District> fetchAllDistricts(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String name
    ) {
        try {
            DistrictPage districtPage = DistrictPage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .sortBy(sortBy)
                    .pageNumber(pageNumber)
                    .build();

            DistrictSearchCriteria districtSearchCriteria = DistrictSearchCriteria
                    .builder()
                    .name(name)
                    .build();
            return districtSearchDao.findAllWithFilters(districtPage, districtSearchCriteria);
        } catch (Exception e) {
            return null;
        }
    }

    public District createDistrict(District request) {

        District district = District.builder()
                .name(request.getName())
                .build();
        return districtRepository.save(district);
    }

    public District updateDistrict(District request, Long id) throws NotFoundException {
        District district = findDistrict(id);

        district.setName(request.getName());
        return districtRepository.save(district);

    }

    public District findDistrict(Long id) throws NotFoundException {
        return districtRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("District with ID " + id + " not found"));
    }

    public DeleteResponse deleteDistrict(Long id) {
        if (districtRepository.existsById(id)) {
            districtRepository.deleteById(id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
