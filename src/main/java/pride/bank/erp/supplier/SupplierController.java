/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;


@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping("/suppliers")
    public ResponseEntity<Page<Supplier>> fetchAllSuppliers(@ModelAttribute SupplierQueryParams params) {
        Page<Supplier> suppliers = supplierService.fetchAllSuppliers(params);
        return new ResponseEntity<>(suppliers, HttpStatus.OK);
    }

    @PostMapping("/suppliers")
    public ResponseEntity<Supplier> createSupplier(@RequestBody SupplierDTO supplier)
            throws NotFoundException {
        Supplier response = supplierService.createSupplier(supplier);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @PutMapping("/suppliers/{id}")
    public ResponseEntity<Supplier> updateSupplier(@PathVariable Long id, @RequestBody SupplierDTO supplier)
            throws NotFoundException {
        Supplier response = supplierService.updateSupplier(supplier, id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @GetMapping("/suppliers/{id}")
    public ResponseEntity<Supplier> getOneSupplier(@PathVariable Long id)
            throws NotFoundException {
        Supplier response = supplierService.findSupplier(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @DeleteMapping("/suppliers/{id}")
    public ResponseEntity<DeleteResponse> deleteSupplier(@PathVariable Long id) {
        DeleteResponse response = supplierService.deleteSupplier(id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(204));
    }

}
