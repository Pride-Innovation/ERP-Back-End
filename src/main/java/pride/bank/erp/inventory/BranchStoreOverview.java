/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One branch's stock position, as shown on the store landing page. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BranchStoreOverview {

    private Long branchId;
    private String branchName;
    private String regionName;

    /** Distinct commodity lines the branch holds. */
    private int itemLines;
    /** Total units on hand across those lines. */
    private long totalQuantity;
    /** Lines at or below their own reorder threshold. */
    private int lowStockLines;
    /** Serialized assets currently booked into the branch's stores. */
    private long assetsHeld;
}
