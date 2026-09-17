package pride.bank.erp.department;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserService;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final UserService userService;
    private final BranchService branchService;

    public Page<DepartmentResponse> fetchAllDepartments(DepartmentQueryParams params) {
        Sort sort = Sort.by(
                Constants.ASCENDING_SORT_DIRECTION.equalsIgnoreCase(params.getSortDirection())
                        ? Sort.Direction.ASC : Sort.Direction.DESC,
                params.getSortBy()
        );
        var pageable = PageRequest.of(params.getPageNumber(), params.getPageSize(), sort);

        boolean hasName = params.getName() != null && !params.getName().isBlank();
        boolean hasBranch = params.getBranchId() != null;

        Page<Department> page;
        if (hasName && hasBranch) {
            page = departmentRepository.findAllByNameContainingIgnoreCaseAndBranch_Id(
                    params.getName(), params.getBranchId(), pageable);
        } else if (hasBranch) {
            page = departmentRepository.findAllByBranch_Id(params.getBranchId(), pageable);
        } else if (hasName) {
            page = departmentRepository.findAllByNameContainingIgnoreCase(params.getName(), pageable);
        } else {
            page = departmentRepository.findAll(pageable);
        }
        return page.map(DepartmentResponse::from);
    }

    public Department findOneDepartment(Long id) throws NotFoundException {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Department with Id %s is not found", id)));
    }

    public DepartmentResponse findOneDepartmentResponse(Long id) throws NotFoundException {
        return DepartmentResponse.from(findOneDepartment(id));
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentDTO request) throws NotFoundException {
        ensureNoDuplicateDepartmentName(request.getName(), null);
        User headOfDepartment = userService.findUser(request.getHeadOfDepartment());
        Branch branch = branchService.findBranch(request.getBranch());
        Department department = Department
                .builder()
                .name(request.getName())
                .headOfDepartment(headOfDepartment)
                .branch(branch)
                .managersGroupEmail(request.getManagersGroupEmail())
                .build();
        return DepartmentResponse.from(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse updateDepartment(DepartmentDTO request, Long id) throws NotFoundException {
        Department department = findOneDepartment(id);
        ensureNoDuplicateDepartmentName(request.getName(), id);
        User headOfDepartment = userService.findUser(request.getHeadOfDepartment());
        Branch branch = branchService.findBranch(request.getBranch());

        department.setName(request.getName());
        department.setHeadOfDepartment(headOfDepartment);
        department.setBranch(branch);
        department.setManagersGroupEmail(request.getManagersGroupEmail());
        return DepartmentResponse.from(departmentRepository.save(department));
    }

    @Transactional
    public DeleteResponse deleteDepartment(Long id) throws NotFoundException {
        findOneDepartment(id);
        departmentRepository.deleteById(id);
        return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
    }

    private void ensureNoDuplicateDepartmentName(String name, Long excludeId) {
        Optional<Department> existing = departmentRepository.findByNameIgnoreCase(name);
        if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
            throw new IllegalArgumentException(
                    String.format("A department with the name '%s' already exists", name));
        }
    }
}
