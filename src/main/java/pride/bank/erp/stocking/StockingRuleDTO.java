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

import java.util.ArrayList;
import java.util.List;

/**
 * Where a category or commodity may be stocked, as sent by Settings.
 *
 * <p>A replacement, not a patch: the rule stored afterwards is exactly this one. {@code override} is
 * read only for a commodity - false hands the commodity back to its category and the other fields are
 * ignored, true makes them the commodity's own rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockingRuleDTO {
    private Boolean override;
    private boolean stockAtHeadOffice;
    private boolean stockAtAllBranches;
    @Builder.Default
    private List<Long> branchIds = new ArrayList<>();
}
