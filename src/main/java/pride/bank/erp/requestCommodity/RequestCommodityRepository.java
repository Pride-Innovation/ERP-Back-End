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
            """)
    Integer getTotalQuantityByMonthAndYear(@Param("month") int month, @Param("year") int year);

    @Query(value = """
                SELECT
                    MONTH(r.create_date) AS month_number,
                    MONTHNAME(r.create_date) AS month_name,
                    SUM(rc.quantity) AS total_quantity
                FROM request_commodity rc
                JOIN request r ON rc.request_id = r.id
                WHERE r.create_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
                GROUP BY MONTH(r.create_date), MONTHNAME(r.create_date)
                ORDER BY MONTH(r.create_date)
            """, nativeQuery = true)
    List<Object[]> getMonthlyCommodityQuantitiesLastYear();
}
