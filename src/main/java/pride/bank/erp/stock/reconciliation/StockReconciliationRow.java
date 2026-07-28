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

/** One order line, with the independent records of it that ought to agree. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockReconciliationRow {

    private Long stockId;
    private String lpoNumber;
    private String stockName;
    private String branchName;
    private String statusName;

    private Long commodityId;
    private String commodityName;
    private String assetTypeName;
    /** Whether this category registers individual assets; consumables do not. */
    private boolean tracksAssets;

    private int orderedQuantity;
    /** What the order line says has been received. */
    private int deliveredQuantity;
    /** What the GRN trail can account for — the sum of every receipt recorded against this line. */
    private int grnCoveredQuantity;
    /** The line's own counter of assets registered from it. */
    private int registeredNumber;
    /** Assets actually on the register for this stock + commodity, excluding disposed ones. */
    private int assetsOnRegister;

    /**
     * Delivered units with no GRN behind them. Every unit received through the proper path is
     * documented by a GRN line and credited to the store in the same transaction, so a positive
     * gap here is the signature of a delivered figure raised through the old correction path:
     * this many units were never added to the store balance.
     */
    private int quantityMissingFromGrn;

    /**
     * Delivered units of an asset-tracked commodity with no asset record. These units exist on the
     * order line but were never registered, so they cannot be assigned, moved or depreciated.
     */
    private int assetsMissing;

    /** True when any independent record disagrees with another. */
    private boolean discrepant;
}
