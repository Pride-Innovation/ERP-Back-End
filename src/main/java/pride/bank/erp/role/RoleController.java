package pride.bank.erp.role;

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
@Tag(name = "Roles", description = "Manage roles – creation, retrieval, update, removal and permission assignment")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {
    private final RoleService roleService;

    @Operation(
            summary = "List roles",
            description = "Returns a paginated list of roles. The optional name filter uses a " +
                    "case-insensitive partial match. When no filters are provided all roles are returned (paginated)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized – missing or invalid JWT token",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden – insufficient permissions",
                    content = @Content)
    })
    @GetMapping("/roles")
    public ResponseEntity<Page<Role>> getRoleWithFilters(
            @Parameter(description = "Filter and pagination parameters")
            @ModelAttribute RoleQueryParams params) {
        return new ResponseEntity<>(roleService.fetchRoles(params), HttpStatus.OK);
    }

    @Operation(
            summary = "Get a role by ID",
            description = "Returns the full details of a single role including its assigned permissions."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Role.class))),
            @ApiResponse(responseCode = "404", description = "Role not found",
                    content = @Content)
    })
    @GetMapping("/roles/{roleID}")
    public ResponseEntity<Role> fetchRole(
            @Parameter(description = "ID of the role", required = true)
            @PathVariable Long roleID)
            throws NotFoundException {
        return new ResponseEntity<>(roleService.findOneRole(roleID), HttpStatus.OK);
    }

    @Operation(
            summary = "Create a role",
            description = "Creates a new role. Name is required and must be unique."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Role created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Role.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate role name",
                    content = @Content)
    })
    @PostMapping("/roles")
    public ResponseEntity<Role> createRole(
            @Valid @RequestBody RoleRequestDTO request) {
        return new ResponseEntity<>(roleService.createRole(request), HttpStatus.CREATED);
    }

    @Operation(
            summary = "Update a role",
            description = "Fully replaces the role identified by {roleID}. Name is required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Role.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate role name",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Role not found",
                    content = @Content)
    })
    @PutMapping("/roles/{roleID}")
    public ResponseEntity<Role> updateRole(
            @Parameter(description = "ID of the role to update", required = true)
            @PathVariable Long roleID,
            @Valid @RequestBody RoleRequestDTO request)
            throws NotFoundException {
        return new ResponseEntity<>(roleService.updateRole(roleID, request), HttpStatus.OK);
    }

    @Operation(
            summary = "Assign a permission to a role",
            description = "Adds the permission identified by {permID} to the role identified by {roleID}. " +
                    "If the permission is already assigned the operation is idempotent."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission assigned successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Role.class))),
            @ApiResponse(responseCode = "404", description = "Role or permission not found",
                    content = @Content)
    })
    @PutMapping("/roles/{roleID}/add-permissions/{permID}")
    public ResponseEntity<Role> assignPermissionsToRole(
            @Parameter(description = "ID of the role", required = true) @PathVariable Long roleID,
            @Parameter(description = "ID of the permission to assign", required = true) @PathVariable Long permID)
            throws NotFoundException {
        return new ResponseEntity<>(roleService.assignPermissionsToRole(roleID, permID), HttpStatus.OK);
    }

    @Operation(
            summary = "Remove a permission from a role",
            description = "Removes the permission identified by {permID} from the role identified by {roleID}. " +
                    "If the permission is not assigned the operation is idempotent."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission removed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Role.class))),
            @ApiResponse(responseCode = "404", description = "Role or permission not found",
                    content = @Content)
    })
    @PutMapping("/roles/{roleID}/remove-permissions/{permID}")
    public ResponseEntity<Role> removePermissionsFromRole(
            @Parameter(description = "ID of the role", required = true) @PathVariable Long roleID,
            @Parameter(description = "ID of the permission to remove", required = true) @PathVariable Long permID)
            throws NotFoundException {
        return new ResponseEntity<>(roleService.removePermissionsFromRole(roleID, permID), HttpStatus.OK);
    }

    @Operation(
            summary = "Delete a role",
            description = "Permanently deletes the role identified by {roleID}."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role deleted successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "404", description = "Role not found",
                    content = @Content)
    })
    @DeleteMapping("/roles/{roleID}")
    public ResponseEntity<DeleteResponse> deleteRole(
            @Parameter(description = "ID of the role to delete", required = true)
            @PathVariable Long roleID)
            throws NotFoundException {
        return new ResponseEntity<>(roleService.deleteRole(roleID), HttpStatus.OK);
    }
}
