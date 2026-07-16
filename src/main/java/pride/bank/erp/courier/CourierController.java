/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.courier;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.util.List;

/** Courier/logistics-carrier directory (vetted couriers registered ahead of dispatch). */
@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class CourierController {

    private final CourierService courierService;

    @GetMapping("/couriers")
    public ResponseEntity<List<CourierDTO>> fetchAll(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return new ResponseEntity<>(courierService.fetchAll(activeOnly), HttpStatus.OK);
    }

    @GetMapping("/couriers/{id}")
    public ResponseEntity<CourierDTO> getOne(@PathVariable Long id) throws NotFoundException {
        return new ResponseEntity<>(courierService.getOne(id), HttpStatus.OK);
    }

    @PostMapping("/couriers")
    public ResponseEntity<CourierDTO> create(@RequestBody CourierDTO request) {
        return new ResponseEntity<>(courierService.create(request), HttpStatus.CREATED);
    }

    @PutMapping("/couriers/{id}")
    public ResponseEntity<CourierDTO> update(@PathVariable Long id, @RequestBody CourierDTO request)
            throws NotFoundException {
        return new ResponseEntity<>(courierService.update(id, request), HttpStatus.OK);
    }

    @DeleteMapping("/couriers/{id}")
    public ResponseEntity<DeleteResponse> delete(@PathVariable Long id) throws NotFoundException {
        return new ResponseEntity<>(courierService.delete(id), HttpStatus.OK);
    }
}
