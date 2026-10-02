/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.util.List;

/**
 * Temporary replacements still out, and the two ways one ends - see {@link LoanerService}.
 *
 * <p>Two of these answer to authentication alone, and that is the design: a loaner is the holder's
 * to hand back, exactly as their own password is theirs to change. The service decides who else may
 * act - {@code REPAIR_ASSET} and the holder within reach - because that depends on <em>whose</em>
 * loaner it is, which a route rule cannot see.
 */
@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class LoanerController {

    private final LoanerService loanerService;
    private final MovementMapper movementMapper;

    /** The caller's own loaners. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/movements/loaners/mine")
    public ResponseEntity<List<LoanerDTO>> mine() {
        return ResponseEntity.ok(loanerService.listMine());
    }

    /** Every loaner still out that the caller may see - the storekeepers' list. */
    @PreAuthorize("hasAuthority('" + Constants.REPAIR_ASSET + "')")
    @GetMapping("/movements/loaners")
    public ResponseEntity<List<LoanerDTO>> outstanding() {
        return ResponseEntity.ok(loanerService.listOutstanding());
    }

    /** Hand a loaner back - by its holder, or by a storekeeper on their behalf. */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/movements/loaners/{assetId}/return")
    public ResponseEntity<MovementResponseDTO> returnLoaner(
            @PathVariable Long assetId,
            @RequestBody(required = false) LoanerReturnDTO dto
    ) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(loanerService.returnLoaner(assetId, dto)), HttpStatus.CREATED);
    }

    /** Keep a loaner for good, when the asset it stood in for is not coming back. */
    @PreAuthorize("hasAuthority('" + Constants.REPAIR_ASSET + "')")
    @PostMapping("/movements/loaners/{assetId}/make-permanent")
    public ResponseEntity<Void> makePermanent(@PathVariable Long assetId) throws NotFoundException {
        loanerService.makePermanent(assetId);
        return ResponseEntity.noContent().build();
    }
}
