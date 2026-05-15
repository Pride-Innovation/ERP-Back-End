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

    public Page<Department> fetchAllDepartments(DepartmentQueryParams params) {
        Sort sort = Sort.by(
                Constants.ASCENDING_SORT_DIRECTION.equalsIgnoreCase(params.getSortDirection())
                        ? Sort.Direction.ASC : Sort.Direction.DESC,
                params.getSortBy()
        );
        var pageable = PageRequest.of(params.getPageNumber(), params.getPageSize(), sort);
        if (params.getName() != null && !params.getName().isBlank()) {
            return departmentRepository.findAllByNameContainingIgnoreCase(params.getName(), pageable);
        }
        return departmentRepository.findAll(pageable);
    }

    public Department findOneDepartment(Long id) throws NotFoundException {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Department with Id %s is not found", id)));
    }

    @Transactional
    public Department createDepartment(DepartmentDTO request) throws NotFoundException {
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
        return departmentRepository.save(department);
    }

    @Transactional
    public Department updateDepartment(DepartmentDTO request, Long id) throws NotFoundException {
        Department department = findOneDepartment(id);
        ensureNoDuplicateDepartmentName(request.getName(), id);
        User headOfDepartment = userService.findUser(request.getHeadOfDepartment());
        Branch branch = branchService.findBranch(request.getBranch());

        department.setName(request.getName());
        department.setHeadOfDepartment(headOfDepartment);
        department.setBranch(branch);
        department.setManagersGroupEmail(request.getManagersGroupEmail());
        return departmentRepository.save(department);
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
