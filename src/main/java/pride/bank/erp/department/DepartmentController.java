package pride.bank.erp.department;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.DeleteResponse;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentService departmentService;

    @GetMapping("/departments")
    public ResponseEntity<Page<Department>> fetchAllDepartments(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<Department> response = departmentService.fetchAllDepartments(pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/departments")
    public ResponseEntity<Department> createDepartment(@RequestBody DepartmentDTO request)
            throws NotFoundException {
        System.out.println("Here we are!!");
        Department response = departmentService.createDepartment(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<Department> updateDepartment(@PathVariable Long id, @RequestBody DepartmentDTO request)
            throws NotFoundException {
        Department response = departmentService.updateDepartment(request, id);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<DeleteResponse> deleteDepartment(@PathVariable Long id) {
        return new ResponseEntity<>(
                departmentService.deleteDepartment(id),
                HttpStatusCode.valueOf(204)
        );
    }
}
