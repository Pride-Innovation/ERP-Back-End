/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.unit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.DeleteResponse;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;
import static pride.bank.erp.helper.Constants.UPDATE_ROLE;

@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
@Tag(name = "Units", description = "Manage units under departments – creation, retrieval, update and removal")
@SecurityRequirement(name = "bearerAuth")
public class UnitController {
    private final UnitService unitService;

    @Operation(summary = "List units",
            description = "Paginated units. Filter by name (partial, case-insensitive) and/or departmentId.")
    @GetMapping("/units")
    public ResponseEntity<Page<UnitResponse>> fetchAllUnits(
            @Parameter(description = "Filter and pagination parameters")
            @ModelAttribute UnitQueryParams params) {
        return new ResponseEntity<>(unitService.fetchAllUnits(params), HttpStatus.OK);
    }

    @Operation(summary = "Create a unit",
            description = "Creates a unit under a department. Unit name must be unique within the department.")
    @PostMapping("/units")
    public ResponseEntity<UnitResponse> createUnit(@Valid @RequestBody UnitDTO request)
            throws NotFoundException {
        return new ResponseEntity<>(unitService.createUnit(request), HttpStatus.CREATED);
    }

    @Operation(summary = "Update a unit",
            description = "Fully replaces the unit identified by {id}.")
    @PutMapping("/units/{id}")
    public ResponseEntity<UnitResponse> updateUnit(
            @Parameter(description = "ID of the unit to update", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UnitDTO request)
            throws NotFoundException {
        return new ResponseEntity<>(unitService.updateUnit(request, id), HttpStatus.OK);
    }

    @Operation(summary = "Get a unit by ID")
    @GetMapping("/units/{id}")
    public ResponseEntity<UnitResponse> getUnit(
            @Parameter(description = "ID of the unit", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        return new ResponseEntity<>(unitService.findOneUnitResponse(id), HttpStatus.OK);
    }

    /**
     * Sets which roles this unit confers.
     *
     * <p>Guarded by {@code UPDATE_ROLE} rather than an update-settings permission: what this changes
     * is who holds which permissions, and it can hand every member of a unit cross-branch write
     * access in one call. It belongs to whoever is trusted to edit roles, not to whoever may rename
     * a unit or correct its group email.
     *
     * <p>{@code @PreAuthorize} rather than a matcher in {@code SecurityConfiguration} because the
     * generic {@code PUT /units/**} rule would otherwise have to be split around this one path, and
     * a rule that only works while it sits above another is exactly the fragility
     * {@code AssetRouteAuthorizationTest} exists to catch.
     */
    @Operation(summary = "Set the roles a unit confers",
            description = "Replaces the unit's roles with the list supplied. Send an empty list to "
                    + "revoke all of them. Everyone in the unit inherits these roles' permissions "
                    + "immediately.")
    @PreAuthorize("hasAuthority('" + UPDATE_ROLE + "')")
    @PutMapping("/units/{id}/roles")
    public ResponseEntity<UnitResponse> setUnitRoles(
            @Parameter(description = "ID of the unit", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UnitRolesDTO request)
            throws NotFoundException {
        return new ResponseEntity<>(unitService.replaceUnitRoles(id, request.getRoleIds()), HttpStatus.OK);
    }

    @Operation(summary = "Delete a unit")
    @DeleteMapping("/units/{id}")
    public ResponseEntity<DeleteResponse> deleteUnit(
            @Parameter(description = "ID of the unit to delete", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        return new ResponseEntity<>(unitService.deleteUnit(id), HttpStatus.OK);
    }
}
