package pride.bank.erp.department;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserService;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final UserService userService;
    private final BranchService branchService;

    public Page<Department> fetchAllDepartments(Pageable pageable) {
        return departmentRepository.findAll(pageable);
    }

    public Department findOneDepartment(Long id) throws NotFoundException {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Department with Id %s is not found", id)));
    }

    public Department createDepartment(DepartmentDTO request)
            throws NotFoundException {
        User headOfDepartment = userService.findUser(request.getHeadOfDepartment());
        Branch branch = branchService.findBranch(request.getBranch());
        Department department = Department
                .builder()
                .name(request.getName())
                .headOfDepartment(headOfDepartment)
                .branch(branch)
                .build();
        return departmentRepository.save(department);
    }

    public Department updateDepartment(DepartmentDTO request, Long id)
            throws NotFoundException {
        User headOfDepartment = userService.findUser(request.getHeadOfDepartment());
        Branch branch = branchService.findBranch(request.getBranch());
        Department department = findOneDepartment(id);

        department.setHeadOfDepartment(headOfDepartment);
        department.setBranch(branch);
        department.setName(request.getName());

        return departmentRepository.save(department);
    }


    public DeleteResponse deleteDepartment(Long id) {
        if (departmentRepository.existsById(id)) {
            departmentRepository.deleteById(id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }

}
