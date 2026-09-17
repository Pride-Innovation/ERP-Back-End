/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.consultant;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.util.List;

/** External repair consultant/vendor directory (used by EXTERNAL repair routing). */
@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class ConsultantController {

    private final ConsultantService consultantService;

    @GetMapping("/consultants")
    public ResponseEntity<List<ConsultantDTO>> fetchAll(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return new ResponseEntity<>(consultantService.fetchAll(activeOnly), HttpStatus.OK);
    }

    @GetMapping("/consultants/{id}")
    public ResponseEntity<ConsultantDTO> getOne(@PathVariable Long id) throws NotFoundException {
        return new ResponseEntity<>(consultantService.getOne(id), HttpStatus.OK);
    }

    @PostMapping("/consultants")
    public ResponseEntity<ConsultantDTO> create(@RequestBody ConsultantDTO request) {
        return new ResponseEntity<>(consultantService.create(request), HttpStatus.CREATED);
    }

    @PutMapping("/consultants/{id}")
    public ResponseEntity<ConsultantDTO> update(@PathVariable Long id, @RequestBody ConsultantDTO request)
            throws NotFoundException {
        return new ResponseEntity<>(consultantService.update(id, request), HttpStatus.OK);
    }

    @DeleteMapping("/consultants/{id}")
    public ResponseEntity<DeleteResponse> delete(@PathVariable Long id) throws NotFoundException {
        return new ResponseEntity<>(consultantService.delete(id), HttpStatus.OK);
    }
}
