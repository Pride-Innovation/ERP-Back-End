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

    /** Every line of every live order — the left-hand side of the reconciliation report. */
    @Query("""
            SELECT sc FROM StockCommodity sc
            JOIN FETCH sc.stock s
            JOIN FETCH sc.commodity c
            WHERE s.disposed = false
            ORDER BY s.id ASC, c.name ASC
            """)
    List<StockCommodity> findAllForActiveStocks();

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

    /**
     * The same six-month stocking summary, narrowed to a branch.
     *
     * <p>The query above has no branch predicate at all, so the stocking trend showed the same
     * national figure to every user regardless of where they work — the one dashboard widget that
     * was neither scoped nor guarded. Null means the national roll-up, so one query serves both.
     *
     * <p>Stock carries its own branch (goods are received *into* a station), so the scope is applied
     * there rather than through the requester or the commodity.
     */
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
                    AND (:branchId IS NULL OR s.branch_id = :branchId)
                GROUP BY
                    DATE_FORMAT(s.create_date, '%Y-%m'), at.name
                ORDER BY
                    month ASC
            """, nativeQuery = true)
    List<MonthlyStockSummaryDTO> getMonthlyStockSummaryForLastSixMonthsScoped(
            @Param("branchId") Long branchId);


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
    List<Object[]> findCurrentMonthStationeryStockSums(@Param("branchId") Long branchId);

    @Query(value = """
                (
                  SELECT c.name AS commodity_name, SUM(sc.delivered_quantity) AS total_quantity
                  FROM stock_commodity sc
                  JOIN commodity c ON sc.commodity_id = c.id
                  JOIN asset_type at ON c.asset_type_id = at.id
                  JOIN stock s ON sc.stock_id = s.id
                  WHERE at.name = 'IT Equipment'
                    AND MONTH(s.create_date) = MONTH(CURRENT_DATE())
                    AND YEAR(s.create_date) = YEAR(CURRENT_DATE())
                  GROUP BY c.name
                  HAVING SUM(sc.delivered_quantity) > 0
                  ORDER BY total_quantity DESC
                  LIMIT 1
                )
                UNION ALL
                (
                  SELECT c.name AS commodity_name, SUM(sc.delivered_quantity) AS total_quantity
                  FROM stock_commodity sc
                  JOIN commodity c ON sc.commodity_id = c.id
                  JOIN asset_type at ON c.asset_type_id = at.id
                  JOIN stock s ON sc.stock_id = s.id
                  WHERE at.name = 'IT Equipment'
                    AND MONTH(s.create_date) = MONTH(CURRENT_DATE())
                    AND YEAR(s.create_date) = YEAR(CURRENT_DATE())
                  GROUP BY c.name
                  HAVING SUM(sc.delivered_quantity) > 0
                  ORDER BY total_quantity ASC
                  LIMIT 1
                )
            """, nativeQuery = true)
    List<Object[]> findTopAndLeastITStockedCommodities(@Param("branchId") Long branchId);


    @Query(value = """
                SELECT * FROM (
                    SELECT c.name AS commodity_name, SUM(sc.delivered_quantity) AS total_quantity
                    FROM stock_commodity sc
                    JOIN commodity c ON sc.commodity_id = c.id
                    JOIN asset_type at ON c.asset_type_id = at.id
                    JOIN stock s ON sc.stock_id = s.id
                    WHERE at.name = 'Office Equipment'
                      AND MONTH(s.create_date) = MONTH(CURRENT_DATE())
                      AND YEAR(s.create_date) = YEAR(CURRENT_DATE())
                    GROUP BY c.name
                    HAVING SUM(sc.delivered_quantity) > 0
                    ORDER BY total_quantity DESC
                    LIMIT 1
                ) AS most_stocked
                UNION ALL
                SELECT * FROM (
                    SELECT c.name AS commodity_name, SUM(sc.delivered_quantity) AS total_quantity
                    FROM stock_commodity sc
                    JOIN commodity c ON sc.commodity_id = c.id
                    JOIN asset_type at ON c.asset_type_id = at.id
                    JOIN stock s ON sc.stock_id = s.id
                    WHERE at.name = 'Office Equipment'
                      AND MONTH(s.create_date) = MONTH(CURRENT_DATE())
                      AND YEAR(s.create_date) = YEAR(CURRENT_DATE())
                    GROUP BY c.name
                    HAVING SUM(sc.delivered_quantity) > 0
                    ORDER BY total_quantity ASC
                    LIMIT 1
                ) AS least_stocked
            """, nativeQuery = true)
    List<Object[]> findTopAndLeastOfficeStockedCommodities();

    @Query(value = """
                SELECT
                    DATE_FORMAT(s.create_date, '%M') AS month,
                    SUM(CASE WHEN at.name = 'IT Equipment' THEN sc.delivered_quantity ELSE 0 END) AS itEquipment,
                    SUM(CASE WHEN at.name = 'Office Equipment' THEN sc.delivered_quantity ELSE 0 END) AS officeEquipment
                FROM stock s
                JOIN stock_commodity sc ON s.id = sc.stock_id
                JOIN commodity c ON c.id = sc.commodity_id
                JOIN asset_type at ON at.id = c.asset_type_id
                WHERE at.name IN ('IT Equipment', 'Office Equipment')
                GROUP BY DATE_FORMAT(s.create_date, '%M'), MONTH(s.create_date)
                ORDER BY MONTH(s.create_date)
            """, nativeQuery = true)
    List<MonthlyStockSummary> getMonthlyStockSummary();
}
