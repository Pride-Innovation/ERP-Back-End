/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.requestCommodity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pride.bank.erp.requestReport.RequestReport;

import java.util.List;
import java.util.Set;

@Repository
public interface RequestCommodityRepository extends JpaRepository<RequestCommodity, Long> {
    @Query("SELECT rc FROM RequestCommodity rc WHERE rc.request.id = :requestId")
    List<RequestCommodity> findByRequestId(@Param("requestId") Long requestId);

    @Modifying
    @Query("DELETE FROM RequestCommodity rc WHERE rc.request.id = :requestId")
    void deleteByRequestId(@Param("requestId") Long requestId);

    @Query("SELECT rr FROM RequestReport rr WHERE rr.request.id = :requestId")
    Set<RequestReport> findReportByRequestId(@Param("requestId") Long requestId);

    @Query("""
                SELECT SUM(rc.quantity)
                FROM RequestCommodity rc
                WHERE MONTH(rc.request.createDate) = :month
                  AND YEAR(rc.request.createDate) = :year
                  AND rc.request.requester.branch.id = :branchId
            """)
    Integer getTotalQuantityByMonthAndYear(
            @Param("month") int month,
            @Param("year") int year,
            @Param("branchId") Long branchId
    );

    @Query(value = """
                SELECT
                    MONTH(r.create_date) AS month_number,
                    MONTHNAME(r.create_date) AS month_name,
                    SUM(rc.quantity) AS total_quantity
                FROM request_commodity rc
                JOIN request r ON rc.request_id = r.id
                JOIN users u ON r.requester_id = u.id
                WHERE r.create_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
                  AND u.branch_id = :branchId
                GROUP BY MONTH(r.create_date), MONTHNAME(r.create_date)
                ORDER BY MONTH(r.create_date)
            """, nativeQuery = true)
    List<Object[]> getMonthlyCommodityQuantitiesLastYearByBranch(@Param("branchId") Long branchId);

    @Query(value = """
                SELECT
                  m.month_number,
                  m.month_name,
                  COALESCE(SUM(CASE WHEN at.name = 'IT Equipment' THEN rc.quantity END), 0) AS itEquipment,
                  COALESCE(SUM(CASE WHEN at.name = 'Office Equipment' THEN rc.quantity END), 0) AS officeEquipment
                FROM (
                  SELECT 1 AS month_number, 'January' AS month_name UNION
                  SELECT 2, 'February' UNION
                  SELECT 3, 'March' UNION
                  SELECT 4, 'April' UNION
                  SELECT 5, 'May' UNION
                  SELECT 6, 'June' UNION
                  SELECT 7, 'July' UNION
                  SELECT 8, 'August' UNION
                  SELECT 9, 'September' UNION
                  SELECT 10, 'October' UNION
                  SELECT 11, 'November' UNION
                  SELECT 12, 'December'
                ) m
                LEFT JOIN request r ON MONTH(r.create_date) = m.month_number AND YEAR(r.create_date) = YEAR(CURDATE())
                LEFT JOIN users u ON r.requester_id = u.id
                LEFT JOIN request_commodity rc ON rc.request_id = r.id
                LEFT JOIN commodity c ON rc.commodity_id = c.id
                LEFT JOIN asset_type at ON c.asset_type_id = at.id
                WHERE u.branch_id = :branchId
                GROUP BY m.month_number, m.month_name
                ORDER BY m.month_number
            """, nativeQuery = true)
    List<Object[]> fetchMonthlyItAndOfficeTotals(@Param("branchId") Long branchId);


    @Query(value = """
                SELECT c.name AS commodity_name, SUM(rc.quantity) AS total_quantity
                FROM request_commodity rc
                JOIN commodity c ON rc.commodity_id = c.id
                JOIN asset_type at ON c.asset_type_id = at.id
                JOIN request r ON rc.request_id = r.id
                WHERE at.name = 'Stationery'
                  AND MONTH(r.create_date) = MONTH(CURRENT_DATE())
                  AND YEAR(r.create_date) = YEAR(CURRENT_DATE())
                GROUP BY c.name
            """, nativeQuery = true)
    List<Object[]> findCurrentMonthStationeryRequestSums();
}
