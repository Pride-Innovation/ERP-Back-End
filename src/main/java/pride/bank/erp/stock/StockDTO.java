/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stock;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.stockCommodity.StockCommodityDTO;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockDTO {
    private Stock stock;
    private Long statusID;
    private Long supplierID;
    private List<StockCommodityDTO> stockCommoditiesRequest;
}
