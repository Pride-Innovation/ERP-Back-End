/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.inventory.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.helper.Constants;

import java.time.LocalDateTime;
import java.util.List;

/** Read access to the stock ledger — the record of how every balance got to where it is. */
@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class StockLedgerController {

    private final StockLedgerService ledgerService;

    /** Ledger entries, newest first, optionally scoped to a store and a date window. */
    @PreAuthorize("hasAuthority('" + Constants.READ_STORE + "')")
    @GetMapping("/inventory/ledger")
    public ResponseEntity<List<StockLedgerEntry>> entries(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return new ResponseEntity<>(ledgerService.entries(storeId, from, to), HttpStatus.OK);
    }

    /** The full history of one commodity in one store — how it reached its current quantity. */
    @PreAuthorize("hasAuthority('" + Constants.READ_STORE + "')")
    @GetMapping("/inventory/ledger/history")
    public ResponseEntity<List<StockLedgerEntry>> history(
            @RequestParam Long storeId,
            @RequestParam Long commodityId
    ) {
        return new ResponseEntity<>(ledgerService.history(storeId, commodityId), HttpStatus.OK);
    }

    /**
     * Recomputes every balance from the ledger and reports where the two disagree.
     *
     * <p>A discrepancy means a balance was changed by something that did not write an entry. Balances
     * that predate the ledger will show here on the first run — that is the historical position the
     * ledger genuinely cannot account for, not a new fault.
     */
    @PreAuthorize("hasAuthority('" + Constants.READ_STORE + "')")
    @GetMapping("/inventory/ledger/verify")
    public ResponseEntity<LedgerVerification> verify() {
        return new ResponseEntity<>(ledgerService.verify(), HttpStatus.OK);
    }
}
