/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stockCommodity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockCommodityRepository extends JpaRepository<StockCommodity, Long> {
    @Query("SELECT sc FROM StockCommodity sc WHERE sc.stock.id = :stockId")
    List<StockCommodity> findByStockId(@Param("stockId") Long stockId);

    @Modifying
    @Query("DELETE FROM StockCommodity sc WHERE sc.stock.id = :stockId")
    void deleteByStockId(@Param("stockId") Long stockId);

    @Query("SELECT sc FROM StockCommodity sc WHERE sc.stock.id IN :stockIds")
    List<StockCommodity> findByStockIdIn(@Param("stockIds") List<Long> stockIds);
}
