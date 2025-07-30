/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.branch;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import pride.bank.erp.district.District;
import pride.bank.erp.district.DistrictService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.region.Region;
import pride.bank.erp.region.RegionService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserService;

import java.util.Objects;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@Service
@RequiredArgsConstructor
public class BranchService {
    private final BranchRepository branchRepository;
    private final BranchSearchDao branchSearchDao;
    private final UserService userService;
    private final DistrictService districtService;
    private final RegionService regionService;

    public Page<BranchWithManagersDTO> fetchAllBranches(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String name
    ) {
        try {
            BranchPage branchPage = BranchPage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .sortBy(sortBy)
                    .pageNumber(pageNumber)
                    .build();

            BranchSearchCriteria branchSearchCriteria = BranchSearchCriteria
                    .builder()
                    .name(name)
                    .build();
            Page<Branch> branches = branchSearchDao.findAllWithFilters(branchPage, branchSearchCriteria);
            return branches.map(this::mapToDto);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public Branch createBranch(BranchDTO request)
            throws NotFoundException {
        User branchManager = userService.findUser(request.getBranchManager());
        User branchOperationsManager = userService.findUser(request.getBranchOperationsManager());
        User creditAdministrator = userService.findUser(request.getCreditAdministrator());
        User relationshipManager = userService.findUser(request.getRelationshipManager());
        District district = districtService.findDistrict(request.getDistrict());
        Region region = regionService.findRegion(request.getRegion());

        Branch branch = Branch.builder()
                .name(request.getName())
                .telephone(request.getTelephone())
                .email(request.getEmail())
                .branchManager(branchManager)
                .branchOperationsManager(branchOperationsManager)
                .creditAdministrator(creditAdministrator)
                .relationshipManager(relationshipManager)
                .district(district)
                .region(region)
                .build();
        return branchRepository.save(branch);
    }

    @Transactional
    public Branch updateBranch(BranchDTO request, Long id) throws NotFoundException {
        Branch branch = findBranch(id);
        User branchManager = userService.findUser(request.getBranchManager());
        User branchOperationsManager = userService.findUser(request.getBranchOperationsManager());
        User creditAdministrator = userService.findUser(request.getCreditAdministrator());
        User relationshipManager = userService.findUser(request.getRelationshipManager());
        District district = districtService.findDistrict(request.getDistrict());
        Region region = regionService.findRegion(request.getRegion());
        branch.setName(request.getName());
        branch.setBranchManager(branchManager);
        branch.setBranchOperationsManager(branchOperationsManager);
        branch.setCreditAdministrator(creditAdministrator);
        branch.setRelationshipManager(relationshipManager);
        branch.setDistrict(district);
        branch.setRegion(region);
        return branchRepository.save(branch);

    }

    public Branch findBranch(Long id) throws NotFoundException {
        return branchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Branch with ID " + id + " not found"));
    }

    public DeleteResponse deleteBranch(Long id) {
        if (branchRepository.existsById(id)) {
            branchRepository.deleteById(id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    private BranchWithManagersDTO mapToDto(Branch branch) {
        return BranchWithManagersDTO.builder()
                .id(branch.getId())
                .name(branch.getName())
                .email(branch.getEmail())
                .telephone(branch.getTelephone())
                .branchManager(mapUser(branch.getBranchManager()))
                .branchOperationsManager(mapUser(branch.getBranchOperationsManager()))
                .relationshipManager(mapUser(branch.getRelationshipManager()))
                .creditAdministrator(mapUser(branch.getCreditAdministrator()))
                .region(branch.getRegion())
                .district(branch.getDistrict())
                .build();
    }

    private User mapUser(User user) {
        if (user == null) return null;
        return User.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .build();
    }
}
