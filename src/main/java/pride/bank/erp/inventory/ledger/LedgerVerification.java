/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.inventory.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Result of recomputing balances from the ledger: where the cache and the history disagree. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LedgerVerification {

    private int balancesChecked;
    private boolean inSync;
    private List<Discrepancy> discrepancies;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Discrepancy {
        private Long storeId;
        private String storeName;
        private Long commodityId;
        private String commodityName;
        /** What the store balance says it holds. */
        private int balanceQuantity;
        /** What the ledger's entries add up to. */
        private int ledgerQuantity;
        /** Balance minus ledger — positive means the balance claims stock the ledger cannot explain. */
        private int difference;
    }
}
