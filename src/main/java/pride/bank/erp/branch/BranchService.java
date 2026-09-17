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

    public Page<BranchWithManagersDTO> fetchAllBranches(BranchQueryParams params) {
        BranchPage branchPage = BranchPage.builder()
                .pageSize(params.getPageSize())
                .sortDirection(determineSortDirection(params.getSortDirection()))
                .sortBy(params.getSortBy())
                .pageNumber(params.getPageNumber())
                .build();

        BranchSearchCriteria branchSearchCriteria = BranchSearchCriteria
                .builder()
                .name(params.getName())
                .regionId(params.getRegionId())
                .districtId(params.getDistrictId())
                .branchManagerId(params.getBranchManagerId())
                .branchOperationsManagerId(params.getBranchOperationsManagerId())
                .relationshipManagerId(params.getRelationshipManagerId())
                .creditAdministratorId(params.getCreditAdministratorId())
                .build();

        Page<Branch> branches = branchSearchDao.findAllWithFilters(branchPage, branchSearchCriteria);
        return branches.map(this::mapToDto);
    }

    @Transactional
    public Branch createBranch(BranchDTO request)
            throws NotFoundException {
        User branchManager = request.getBranchManager() != null ? userService.findUser(request.getBranchManager()) : null;
        User branchOperationsManager = request.getBranchOperationsManager() != null ? userService.findUser(request.getBranchOperationsManager()) : null;
        User creditAdministrator = request.getCreditAdministrator() != null ? userService.findUser(request.getCreditAdministrator()) : null;
        User relationshipManager = request.getRelationshipManager() != null ? userService.findUser(request.getRelationshipManager()) : null;
        District district = districtService.findDistrict(request.getDistrict());
        Region region = regionService.findRegion(request.getRegion());

        Branch branch = Branch.builder()
                .name(request.getName())
                .telephone(request.getTelephone())
                .email(request.getEmail())
                .managersGroupEmail(request.getManagersGroupEmail())
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
        User branchManager = request.getBranchManager() != null ? userService.findUser(request.getBranchManager()) : null;
        User branchOperationsManager = request.getBranchOperationsManager() != null ? userService.findUser(request.getBranchOperationsManager()) : null;
        User creditAdministrator = request.getCreditAdministrator() != null ? userService.findUser(request.getCreditAdministrator()) : null;
        User relationshipManager = request.getRelationshipManager() != null ? userService.findUser(request.getRelationshipManager()) : null;
        District district = districtService.findDistrict(request.getDistrict());
        Region region = regionService.findRegion(request.getRegion());
        branch.setName(request.getName());
        branch.setTelephone(request.getTelephone());
        branch.setEmail(request.getEmail());
        branch.setManagersGroupEmail(request.getManagersGroupEmail());
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

        public BranchWithManagersDTO findBranchDetails(Long id) throws NotFoundException {
        BranchDetailsProjection branch = branchRepository.findBranchDetailsById(id)
            .orElseThrow(() -> new NotFoundException("Branch with ID " + id + " not found"));

        return BranchWithManagersDTO.builder()
            .id(branch.getId())
            .name(branch.getName())
            .email(branch.getEmail())
            .telephone(branch.getTelephone())
            .managersGroupEmail(branch.getManagersGroupEmail())
            .isHeadOffice(branch.getHeadOffice())
            .branchManager(mapUser(
                branch.getBranchManagerId(),
                branch.getBranchManagerFirstName(),
                branch.getBranchManagerLastName(),
                branch.getBranchManagerEmail()
            ))
            .branchOperationsManager(mapUser(
                branch.getBranchOperationsManagerId(),
                branch.getBranchOperationsManagerFirstName(),
                branch.getBranchOperationsManagerLastName(),
                branch.getBranchOperationsManagerEmail()
            ))
            .relationshipManager(mapUser(
                branch.getRelationshipManagerId(),
                branch.getRelationshipManagerFirstName(),
                branch.getRelationshipManagerLastName(),
                branch.getRelationshipManagerEmail()
            ))
            .creditAdministrator(mapUser(
                branch.getCreditAdministratorId(),
                branch.getCreditAdministratorFirstName(),
                branch.getCreditAdministratorLastName(),
                branch.getCreditAdministratorEmail()
            ))
            .region(branch.getRegion())
            .district(branch.getDistrict())
            .build();
        }

    public DeleteResponse deleteBranch(Long id) throws NotFoundException {
        if (!branchRepository.existsById(id)) {
            throw new NotFoundException("Branch with ID " + id + " not found");
        }
        branchRepository.deleteById(id);
        return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
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
                .managersGroupEmail(branch.getManagersGroupEmail())
                .isHeadOffice(branch.isHeadOffice())
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

    private User mapUser(Long id, String firstName, String lastName, String email) {
        if (id == null) {
            return null;
        }

        return User.builder()
                .id(id)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .build();
    }

    public Branch findBranchByName(String name) {
        return branchRepository.findByName(name).orElse(null);
    }
}
