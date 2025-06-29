/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;


@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class StockController {
    private final StockService stockService;

    @GetMapping("/stocks")
    public ResponseEntity<Page<Stock>> fetchAllStocks(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = ASCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String lpoNumber
    ) {
        Page<Stock> response = stockService.fetchAllStocks(
                pageSize,
                sortDirection,
                pageNumber,
                sortBy,
                lpoNumber
        );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/stocks")
    public ResponseEntity<Stock> createStock(@RequestBody StockDTO stockDTO) {
        try {
            Stock response = stockService.createStock(
                    stockDTO.getStock(),
                    stockDTO.getStatus(),
                    stockDTO.getStockCommoditiesRequest()
            );
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            return null;
        }

    }

    @PutMapping("/stocks/{stockID}")
    public ResponseEntity<Stock> updateStock(@PathVariable Long stockID, @RequestBody StockDTO stockDTO)
            throws NotFoundException {
        Stock response = stockService.updateStock(
                stockID,
                stockDTO.getStock(),
                stockDTO.getStatus(),
                stockDTO.getSupplier(),
                stockDTO.getStockCommoditiesRequest()
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/stocks/{stockID}")
    public ResponseEntity<StockResponseDTO> getOneStock(@PathVariable Long stockID)
            throws NotFoundException {
        return new ResponseEntity<>(stockService.fetchSingleStock(stockID), HttpStatus.OK);
    }
}
