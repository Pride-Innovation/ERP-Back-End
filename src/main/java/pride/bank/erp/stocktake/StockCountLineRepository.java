/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockCountLineRepository extends JpaRepository<StockCountLine, Long> {

    List<StockCountLine> findByStockCount_IdOrderByCommodity_NameAsc(Long stockCountId);

    Optional<StockCountLine> findByStockCount_IdAndCommodity_Id(Long stockCountId, Long commodityId);
}
