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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.stockReceipt.StockReceiptDTO;
import pride.bank.erp.stockReceipt.StockReceiptService;
import pride.bank.erp.stockReceipt.StockReceiptView;

import java.time.LocalDateTime;
import java.util.List;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;


@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
@Slf4j
public class StockController {
    private final StockService stockService;
    private final StockReceiptService stockReceiptService;

    @GetMapping("/stocks")
    public ResponseEntity<Page<StockDAO>> fetchAllStocks(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = DESCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String lpoNumber,
            @RequestParam(required = false) Long stockStatusId,
            /*
             * Declared, because Spring drops a parameter no endpoint names without complaining.
             * The page has been sending `supplier` as a partial name for as long as the filter has
             * existed; it never reached this method, so the box looked like it worked and returned
             * the unfiltered list.
             */
            @RequestParam(required = false) Long supplierId,
            /*
             * Which branch's deliveries to show.
             *
             * Only meaningful at ALL scope — the resolver refuses a branch that is not the caller's
             * below that, so this cannot be used to widen. It exists so Head Office and the units,
             * who see every branch by default, can look at one at a time.
             */
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false, defaultValue = "false") Boolean disposed,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate
    ) {
        Page<StockDAO> response = stockService.fetchAllStocks(
                pageSize,
                sortDirection,
                pageNumber,
                sortBy,
                lpoNumber,
                stockStatusId,
                supplierId,
                branchId,
                disposed,
                startDate,
                endDate
        );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/stocks")
    public ResponseEntity<?> createStock(@RequestBody StockDTO stockDTO) {
        log.info("Received stock creation request: {}", stockDTO);
        try {
            Stock response = stockService.createStock(
                    stockDTO.getStock(),
                    stockDTO.getSupplier(),
                    stockDTO.getStockCommoditiesRequest(),
                    stockDTO.getIdempotencyKey()
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

    /**
     * Corrects an existing order. Quantities <em>delivered</em> are not editable here — see
     * {@link #receiveDelivery}. The status is derived from the lines, so no status id is accepted.
     */
    @PutMapping("/stocks/{stockID}")
    public ResponseEntity<Stock> updateStock(@PathVariable Long stockID, @RequestBody StockDTO stockDTO)
            throws NotFoundException {
        Stock response = stockService.updateStock(
                stockID,
                stockDTO.getStock(),
                stockDTO.getSupplier(),
                stockDTO.getStockCommoditiesRequest()
        );

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/stocks/{stockID}")
    public ResponseEntity<StockResponseDTO> getOneStock(@PathVariable Long stockID)
            throws NotFoundException {
        return new ResponseEntity<>(stockService.fetchSingleStock(stockID), HttpStatus.OK);
    }

    /**
     * Records one physical delivery against the order: every line that arrived in this batch, plus
     * the delivery-note details a GRN is meant to carry. One call produces one receipt, one GRN,
     * one store credit and one batch of assets.
     */
    @PostMapping("/stocks/{stockID}/receipts")
    public ResponseEntity<Stock> receiveDelivery(
            @PathVariable Long stockID,
            @RequestBody StockReceiptDTO request
    ) throws NotFoundException {
        Stock response = stockService.receiveDelivery(stockID, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /** The delivery history of an order — one entry per physical receipt, newest last. */
    @GetMapping("/stocks/{stockID}/receipts")
    public ResponseEntity<List<StockReceiptView>> fetchReceipts(@PathVariable Long stockID) {
        return new ResponseEntity<>(stockReceiptService.fetchReceiptsForStock(stockID), HttpStatus.OK);
    }

    /**
     * @deprecated superseded by {@link #receiveDelivery}, which records the receipt date, delivery
     * note and receiving officer. Retained so older clients keep working.
     */
    @Deprecated
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

    @PostMapping("/stocks/{stockID}/close-short")
    public ResponseEntity<Stock> closeShort(
            @PathVariable Long stockID,
            @RequestBody(required = false) CloseShortDTO request
    ) throws NotFoundException {
        Stock response = stockService.closeShort(stockID, request != null ? request.getReason() : null);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /*
     * Guarded here, not by a matcher.
     *
     * INVENTORY_ROUTE is `/stocks/**`, and this path is not under it — the name says "inventory" and
     * the value covers one module's URLs, which is the trap recorded in CLAUDE.md. So deleting a
     * stock receipt fell through to `.anyRequest().authenticated()` and was open to any signed-in
     * user. The branch check in the service applies on top.
     */
    @PreAuthorize("hasAuthority('" + Constants.DELETE_INVENTORY + "')")
    @PostMapping("/delete-stock/{stockID}")
    public ResponseEntity<DeleteResponse> deleteDelivery(
            @PathVariable Long stockID
    ) throws NotFoundException {

        DeleteResponse response = stockService.deleteStock(stockID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
