package pride.bank.erp.department;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.DeleteResponse;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Manage bank departments – creation, retrieval, update and removal")
@SecurityRequirement(name = "bearerAuth")
public class DepartmentController {
    private final DepartmentService departmentService;

    @Operation(
            summary = "List departments",
            description = "Returns a paginated list of departments. The optional name filter uses a " +
                    "case-insensitive partial match. When no filters are provided all departments are returned (paginated)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Departments retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized – missing or invalid JWT token",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden – insufficient permissions",
                    content = @Content)
    })
    @GetMapping("/departments")
    public ResponseEntity<Page<Department>> fetchAllDepartments(
            @Parameter(description = "Filter and pagination parameters")
            @ModelAttribute DepartmentQueryParams params) {
        Page<Department> response = departmentService.fetchAllDepartments(params);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Create a department",
            description = "Creates a new department. Name, head of department and branch are required. " +
                    "Department name must be unique."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Department created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Department.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate department name",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Referenced user or branch not found",
                    content = @Content)
    })
    @PostMapping("/departments")
    public ResponseEntity<Department> createDepartment(
            @Valid @RequestBody DepartmentDTO request)
            throws NotFoundException {
        Department response = departmentService.createDepartment(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Update a department",
            description = "Fully replaces the department identified by {id}. Name, head of department and branch are required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Department updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Department.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate department name",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Department, referenced user or branch not found",
                    content = @Content)
    })
    @PutMapping("/departments/{id}")
    public ResponseEntity<Department> updateDepartment(
            @Parameter(description = "ID of the department to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody DepartmentDTO request)
            throws NotFoundException {
        Department response = departmentService.updateDepartment(request, id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Get a department by ID",
            description = "Returns the full details of a single department including its head of department and branch."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Department found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Department.class))),
            @ApiResponse(responseCode = "404", description = "Department not found",
                    content = @Content)
    })
    @GetMapping("/departments/{id}")
    public ResponseEntity<Department> getDepartment(
            @Parameter(description = "ID of the department", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        return new ResponseEntity<>(departmentService.findOneDepartment(id), HttpStatus.OK);
    }

    @Operation(
            summary = "Delete a department",
            description = "Permanently deletes the department identified by {id}."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Department deleted successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "404", description = "Department not found",
                    content = @Content)
    })
    @DeleteMapping("/departments/{id}")
    public ResponseEntity<DeleteResponse> deleteDepartment(
            @Parameter(description = "ID of the department to delete", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        return new ResponseEntity<>(departmentService.deleteDepartment(id), HttpStatus.OK);
    }
}
