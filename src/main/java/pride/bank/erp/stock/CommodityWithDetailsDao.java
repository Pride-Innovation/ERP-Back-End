/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.commodity.Commodity;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommodityWithDetailsDao {
    private Commodity commodity;
    private int orderedQuantity;
    private int deliveredQuantity;
    private int costPrice;
    private int purchasePrice;
}
