package pride.bank.erp.permission;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.DeleteResponse;

import java.util.List;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Manage permissions – creation, retrieval, update and removal")
@SecurityRequirement(name = "bearerAuth")
public class PermissionController {
    private final PermissionService permissionService;

    @Operation(
            summary = "List all permissions",
            description = "Returns the full list of permissions. Permissions are not paginated as they form a " +
                    "bounded reference set."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permissions retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = List.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized – missing or invalid JWT token",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden – insufficient permissions",
                    content = @Content)
    })
    @GetMapping("/permissions")
    public ResponseEntity<List<Permission>> fetchAllPermissions() {
        return new ResponseEntity<>(permissionService.fetchAllPermissions(), HttpStatus.OK);
    }

    @Operation(
            summary = "Create a permission",
            description = "Creates a new permission. Name is required and must be unique."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Permission created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Permission.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate permission name",
                    content = @Content)
    })
    @PostMapping("/permissions")
    public ResponseEntity<Permission> createPermission(
            @Valid @RequestBody PermissionRequestDTO request) {
        return new ResponseEntity<>(permissionService.createPermission(request), HttpStatus.CREATED);
    }

    @Operation(
            summary = "Update a permission",
            description = "Replaces the name of the permission identified by {permID}. Name must be unique."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Permission.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate permission name",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Permission not found",
                    content = @Content)
    })
    @PutMapping("/permissions/{permID}")
    public ResponseEntity<Permission> updatePermission(
            @Parameter(description = "ID of the permission to update", required = true)
            @PathVariable Long permID,
            @Valid @RequestBody PermissionRequestDTO request)
            throws NotFoundException {
        return new ResponseEntity<>(permissionService.updatePermission(permID, request), HttpStatus.OK);
    }

    @Operation(
            summary = "Delete a permission",
            description = "Permanently deletes the permission identified by {permID} and removes it from all " +
                    "roles it was assigned to."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission deleted successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "404", description = "Permission not found",
                    content = @Content)
    })
    @DeleteMapping("/permissions/{permID}")
    public ResponseEntity<DeleteResponse> deletePermission(
            @Parameter(description = "ID of the permission to delete", required = true)
            @PathVariable Long permID)
            throws NotFoundException {
        return new ResponseEntity<>(permissionService.deletePermission(permID), HttpStatus.OK);
    }
}
