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
import java.util.Optional;

@Repository
public interface StockCommodityRepository extends JpaRepository<StockCommodity, Long> {
    @Query("SELECT sc FROM StockCommodity sc WHERE sc.stock.id = :stockId")
    List<StockCommodity> findByStockId(@Param("stockId") Long stockId);

    @Modifying
    @Query("DELETE FROM StockCommodity sc WHERE sc.stock.id = :stockId")
    void deleteByStockId(@Param("stockId") Long stockId);

    @Query("SELECT sc FROM StockCommodity sc WHERE sc.stock.id IN :stockIds")
    List<StockCommodity> findByStockIdIn(@Param("stockIds") List<Long> stockIds);

    Optional<StockCommodity> findByStockIdAndCommodityId(Long stockId, Long commodityId);

    @Query(value = """
                SELECT
                    DATE_FORMAT(s.create_date, '%Y-%m') AS month,
                    at.name AS assetType,
                    SUM(sc.delivered_quantity) AS totalQuantity
                FROM
                    stock_commodity sc
                JOIN
                    stock s ON sc.stock_id = s.id
                JOIN
                    commodity c ON sc.commodity_id = c.id
                JOIN
                    asset_type at ON c.asset_type_id = at.id
                WHERE
                    s.create_date >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 5 MONTH), '%Y-%m-01')
                GROUP BY
                    DATE_FORMAT(s.create_date, '%Y-%m'), at.name
                ORDER BY
                    month ASC
            """, nativeQuery = true)
    List<MonthlyStockSummaryDTO> getMonthlyStockSummaryForLastSixMonths();


    @Query(value = """
                SELECT c.name AS commodity_name, SUM(sc.ordered_quantity) AS total_quantity
                FROM stock_commodity sc
                JOIN commodity c ON sc.commodity_id = c.id
                JOIN asset_type at ON c.asset_type_id = at.id
                JOIN stock s ON sc.stock_id = s.id
                WHERE at.name = 'Stationery'
                  AND MONTH(s.create_date) = MONTH(CURRENT_DATE())
                  AND YEAR(s.create_date) = YEAR(CURRENT_DATE())
                GROUP BY c.name
            """, nativeQuery = true)
    List<Object[]> findCurrentMonthStationeryStockSums();


}
