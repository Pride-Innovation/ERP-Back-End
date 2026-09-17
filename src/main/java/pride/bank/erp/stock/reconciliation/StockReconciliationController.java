/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock.reconciliation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.helper.Constants;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class StockReconciliationController {

    private final StockReconciliationService stockReconciliationService;

    /**
     * Lines where the order, the GRN trail and the asset register disagree.
     *
     * @param onlyDiscrepancies {@code true} (default) to return only the offending lines;
     *                          {@code false} to return every line, for export.
     */
    @GetMapping("/stock-reconciliation")
    public ResponseEntity<StockReconciliationReport> fetchReport(
            @RequestParam(required = false, defaultValue = "true") boolean onlyDiscrepancies
    ) {
        return new ResponseEntity<>(
                stockReconciliationService.buildReport(onlyDiscrepancies), HttpStatus.OK);
    }
}
