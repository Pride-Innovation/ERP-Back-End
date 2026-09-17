/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The reconciliation report: headline counts plus the offending lines. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockReconciliationReport {

    private int linesExamined;
    private int linesWithDiscrepancies;
    /** Total units received on paper but never credited to a store. */
    private int totalQuantityMissingFromGrn;
    /** Total asset records that should exist for received units but do not. */
    private int totalAssetsMissing;
    private List<StockReconciliationRow> rows;
}
