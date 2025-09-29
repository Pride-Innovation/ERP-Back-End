/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.time.LocalDateTime;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;


@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
@Slf4j
public class StockController {
    private final StockService stockService;

    @GetMapping("/stocks")
    public ResponseEntity<Page<StockDAO>> fetchAllStocks(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = DESCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String lpoNumber,
            @RequestParam(required = false) Long stockStatusId,
            @RequestParam(required = false, defaultValue = "false") Boolean disposed,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate
    ) {
        try {
            Page<StockDAO> response = stockService.fetchAllStocks(
                    pageSize,
                    sortDirection,
                    pageNumber,
                    sortBy,
                    lpoNumber,
                    stockStatusId,
                    disposed,
                    startDate,
                    endDate
            );
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception ex) {
            return null;
        }

    }

    @PostMapping("/stocks")
    public ResponseEntity<?> createStock(@RequestBody StockDTO stockDTO) {
        log.info("Received stock creation request: {}", stockDTO);
        try {
            Stock response = stockService.createStock(
                    stockDTO.getStock(),
                    stockDTO.getSupplier(),
                    stockDTO.getStockCommoditiesRequest()
            );
            log.info("Stock created successfully with ID: {}", response.getId());
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (NotFoundException e) {
            log.error("Failed to create stock", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Failed to create stock", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create stock", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
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

    @PostMapping("/stocks/{stockID}/complete-delivery")
    public ResponseEntity<Stock> completeDelivery(
            @RequestBody completeDeliveryDTO request,
            @PathVariable Long stockID
    )
            throws NotFoundException {
        Stock response = stockService.completeDelivery(
                stockID,
                request.getAdditionalDeliveries(),
                request.grnNumber
        );
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/delete-stock/{stockID}")
    public ResponseEntity<DeleteResponse> deleteDelivery(
            @PathVariable Long stockID
    ) throws NotFoundException {

        DeleteResponse response = stockService.deleteStock(stockID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
