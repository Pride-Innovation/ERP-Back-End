/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.unitofmeasure;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.util.List;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

/**
 * Settings → Units of Measure.
 *
 * <p>The read is open to any signed-in user, like the commodity and category lists: the commodity
 * form, the asset form and the import template all offer it, and gating it on READ_SETTING would empty
 * those dropdowns for everyone outside Settings. Adding and switching off are Settings writes.
 */
@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
@Tag(name = "Units of measure", description = "The units a commodity or asset may be counted in")
@SecurityRequirement(name = "bearerAuth")
public class UnitOfMeasureController {

    private final UnitOfMeasureService unitService;

    public record CreateUnitRequest(String name) {}

    public record ActiveRequest(Boolean active) {}

    @GetMapping("/units-of-measure")
    public ResponseEntity<List<UnitOfMeasure>> list(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(unitService.list(includeInactive));
    }

    @PreAuthorize("hasAuthority('" + Constants.CREATE_SETTING + "')")
    @PostMapping("/units-of-measure")
    public ResponseEntity<UnitOfMeasure> create(@RequestBody CreateUnitRequest request) {
        return new ResponseEntity<>(unitService.create(request == null ? null : request.name()), HttpStatus.CREATED);
    }

    @PreAuthorize("hasAuthority('" + Constants.UPDATE_SETTING + "')")
    @PutMapping("/units-of-measure/{id}/active")
    public ResponseEntity<UnitOfMeasure> setActive(@PathVariable Long id, @RequestBody ActiveRequest request)
            throws NotFoundException {
        if (request == null || request.active() == null) {
            throw new pride.bank.erp.exception.BadRequestException("Say whether the unit should be on or off.");
        }
        return ResponseEntity.ok(unitService.setActive(id, request.active()));
    }
}
