/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.district;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.DeleteResponse;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;
import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;


@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
public class DistrictController {
    private final DistrictService districtService;

    @PostMapping("/districts")
    public ResponseEntity<District> createDistrict(@RequestBody District district) {
        District response = districtService.createDistrict(district);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @GetMapping("/districts")
    public ResponseEntity<Page<District>> listDistricts(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = ASCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String name
    ) {

        Page<District> districts = districtService.fetchAllDistricts(
                pageSize,
                sortDirection,
                pageNumber,
                sortBy,
                name
        );
        return new ResponseEntity<>(districts, HttpStatus.OK);
    }

    @PutMapping("/districts/{id}")
    public ResponseEntity<District> updateDistrict(
            @RequestBody District request,
            @PathVariable Long id
    ) throws NotFoundException {
        District response = districtService.updateDistrict(request, id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @DeleteMapping("/districts/{id}")
    public ResponseEntity<DeleteResponse> deleteDistrict(@PathVariable Long id) {
        DeleteResponse response = districtService.deleteDistrict(id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @GetMapping("/districts/{id}")
    public ResponseEntity<District> getOneDistrict(@PathVariable Long id) throws NotFoundException {
        District response = districtService.findDistrict(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
