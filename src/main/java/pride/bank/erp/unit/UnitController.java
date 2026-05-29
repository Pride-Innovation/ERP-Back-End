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
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.DeleteResponse;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

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

    @Operation(summary = "Delete a unit")
    @DeleteMapping("/units/{id}")
    public ResponseEntity<DeleteResponse> deleteUnit(
            @Parameter(description = "ID of the unit to delete", required = true)
            @PathVariable Long id)
            throws NotFoundException {
        return new ResponseEntity<>(unitService.deleteUnit(id), HttpStatus.OK);
    }
}
