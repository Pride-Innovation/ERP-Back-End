/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.region;

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
public class RegionController {

    private final RegionService regionService;

    @GetMapping("/regions")
    public ResponseEntity<Page<Region>> fetchAllTitles(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = ASCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String name
    ) {
        Page<Region> response = regionService.fetchAllRegions(
                pageSize,
                sortDirection,
                pageNumber,
                sortBy,
                name
        );

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/regions")
    public ResponseEntity<Region> createRegion(@RequestBody Region region) {
        Region response = regionService.createRegion(region);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/regions/{id}")
    public ResponseEntity<Region> updateRegion(@RequestBody Region region, @PathVariable Long id)
            throws NotFoundException {
        Region response = regionService.updateRegion(region, id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @DeleteMapping("/regions/{id}")
    public ResponseEntity<DeleteResponse> deleteRegion(@PathVariable Long id) {
        DeleteResponse response = regionService.deleteRegion(id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @GetMapping("/regions/{id}")
    public ResponseEntity<Region> getSingleRegion(@PathVariable Long id)
            throws NotFoundException {
        return new ResponseEntity<>(regionService.findRegion(id), HttpStatus.OK);
    }
}
