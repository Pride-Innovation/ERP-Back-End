/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.stocktake.StockTakeDTOs.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Stock take: counting a store's consumables and reconciling the balances to what is on the shelf.
 *
 * <p>The workflow is documented in {@code docs/stock-take-api.md}.
 */
@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class StockTakeController {

    private final StockTakeService stockTakeService;

    /** Opens a blind count sheet for a store, snapshotting current system quantities. */
    @PreAuthorize("hasAuthority('" + Constants.PERFORM_STOCK_TAKE + "')")
    @PostMapping("/stock-counts")
    public ResponseEntity<CountView> open(@RequestBody OpenCountRequest request) throws NotFoundException {
        return new ResponseEntity<>(stockTakeService.openCount(request), HttpStatus.CREATED);
    }

    /**
     * The stores the caller may open a count for - at their own branch, with stock lines to count.
     * The Open dialog reads this rather than filtering the store directory itself, which used the
     * general inventory reach and would have offered Head Office every branch's stores.
     */
    @PreAuthorize("hasAuthority('" + Constants.PERFORM_STOCK_TAKE + "')")
    @GetMapping("/stock-counts/countable-stores")
    public ResponseEntity<List<CountableStore>> countableStores() {
        return new ResponseEntity<>(stockTakeService.countableStores(), HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.READ_STOCK_TAKE + "')")
    @GetMapping("/stock-counts")
    public ResponseEntity<List<CountView>> list(@RequestParam(required = false) Long storeId) {
        return new ResponseEntity<>(stockTakeService.list(storeId), HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.READ_STOCK_TAKE + "')")
    @GetMapping("/stock-counts/{countId}")
    public ResponseEntity<CountView> fetch(@PathVariable Long countId) throws NotFoundException {
        return new ResponseEntity<>(stockTakeService.fetch(countId), HttpStatus.OK);
    }

    /** Records counted quantities. Safe to call repeatedly as the count progresses. */
    @PreAuthorize("hasAuthority('" + Constants.PERFORM_STOCK_TAKE + "')")
    @PutMapping("/stock-counts/{countId}/lines")
    public ResponseEntity<CountView> saveCounts(
            @PathVariable Long countId,
            @RequestBody SaveCountsRequest request
    ) throws NotFoundException {
        return new ResponseEntity<>(stockTakeService.saveCounts(countId, request), HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.PERFORM_STOCK_TAKE + "')")
    @PostMapping("/stock-counts/{countId}/submit")
    public ResponseEntity<CountView> submit(@PathVariable Long countId) throws NotFoundException {
        return new ResponseEntity<>(stockTakeService.submit(countId), HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.APPROVE_STOCK_TAKE + "')")
    @PostMapping("/stock-counts/{countId}/approve")
    public ResponseEntity<CountView> approve(
            @PathVariable Long countId,
            @RequestBody(required = false) ReviewRequest request
    ) throws NotFoundException {
        return new ResponseEntity<>(stockTakeService.approve(countId, request), HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.APPROVE_STOCK_TAKE + "')")
    @PostMapping("/stock-counts/{countId}/send-back")
    public ResponseEntity<CountView> sendBack(
            @PathVariable Long countId,
            @RequestBody ReviewRequest request
    ) throws NotFoundException {
        return new ResponseEntity<>(stockTakeService.sendBack(countId, request), HttpStatus.OK);
    }

    /** Writes the approved variances to the balances. The only step that changes stock. */
    @PreAuthorize("hasAuthority('" + Constants.APPROVE_STOCK_TAKE + "')")
    @PostMapping("/stock-counts/{countId}/post")
    public ResponseEntity<CountView> post(@PathVariable Long countId) throws NotFoundException {
        return new ResponseEntity<>(stockTakeService.post(countId), HttpStatus.OK);
    }

    /**
     * Either permission: an approver may cancel anything not yet posted, and the counter may cancel
     * their own count while it is still being counted — see {@code StockTakeService#cancel}.
     */
    @PreAuthorize("hasAnyAuthority('" + Constants.PERFORM_STOCK_TAKE + "', '" + Constants.APPROVE_STOCK_TAKE + "')")
    @PostMapping("/stock-counts/{countId}/cancel")
    public ResponseEntity<CountView> cancel(
            @PathVariable Long countId,
            @RequestBody(required = false) ReviewRequest request
    ) throws NotFoundException {
        return new ResponseEntity<>(stockTakeService.cancel(countId, request), HttpStatus.OK);
    }

    /** Adjustment history: what was corrected, by how much, and why. */
    @PreAuthorize("hasAuthority('" + Constants.READ_STOCK_TAKE + "')")
    @GetMapping("/stock-adjustments")
    public ResponseEntity<VarianceReport> varianceReport(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Long assetTypeId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "25") int pageSize
    ) {
        return new ResponseEntity<>(stockTakeService.varianceReport(AdjustmentQuery.builder()
                .storeId(storeId).branchId(branchId).reason(parseReason(reason)).assetTypeId(assetTypeId)
                .search(search).from(from).to(to).pageNumber(pageNumber).pageSize(pageSize)
                .build()), HttpStatus.OK);
    }

    /** An unknown reason is a 400, never "no filter": ignoring it would answer with every reason. */
    static AdjustmentReason parseReason(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return AdjustmentReason.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown adjustment reason '" + raw + "'.");
        }
    }
}
