/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * What the caller's branch may stock - the inventory form's pickers are filtered by it.
 *
 * <p>A category is listed when its own rule admits the branch or any of its commodities does, so a
 * category kept at Head Office whose toner is overridden for branches still appears, offering only
 * the toner.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockableDTO {
    private Long branchId;
    private String branchName;
    private boolean headOffice;
    private List<Long> assetTypeIds;
    private List<Long> commodityIds;
}
