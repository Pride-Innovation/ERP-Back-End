/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.commodity;

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
public class CommodityController {

    private final CommodityService commodityService;

    @GetMapping("/commodities")
    public ResponseEntity<Page<Commodity>> listCommodities(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = ASCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String name
    ) {
        Page<Commodity> response = commodityService.fetchAllCommodities(
                pageSize,
                sortDirection,
                pageNumber,
                sortBy,
                name
        );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/commodities")
    public ResponseEntity<Commodity> createCommodity(@RequestBody CommodityDTO commodity)
            throws NotFoundException {
        Commodity response = commodityService.createCommodity(commodity);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/commodities/{id}")
    public ResponseEntity<Commodity> updateCommodity(@RequestBody CommodityDTO commodity, @PathVariable Long id)
            throws NotFoundException {
        Commodity response = commodityService.updateCommodity(commodity, id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @DeleteMapping("/commodities/{id}")
    public ResponseEntity<DeleteResponse> deleteCommodity(@PathVariable Long id) {
        DeleteResponse response = commodityService.deleteCommodity(id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(204));
    }

    @GetMapping("/commodities/{id}")
    public ResponseEntity<Commodity> getSingleCommodity(@PathVariable Long id)
            throws NotFoundException {
        Commodity commodity = commodityService.findCommodity(id);
        return new ResponseEntity<>(commodity, HttpStatus.OK);
    }
}
