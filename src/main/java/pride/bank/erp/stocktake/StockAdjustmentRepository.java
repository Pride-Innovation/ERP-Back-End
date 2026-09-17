/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    List<StockAdjustment> findByStockCount_Id(Long stockCountId);

    /** Variance report: every adjustment in a window, newest first, optionally scoped to a store. */
    @Query("""
            SELECT a FROM StockAdjustment a
            WHERE (:storeId IS NULL OR a.store.id = :storeId)
              AND (:from IS NULL OR a.postedAt >= :from)
              AND (:to IS NULL OR a.postedAt <= :to)
            ORDER BY a.postedAt DESC
            """)
    List<StockAdjustment> findForReport(@Param("storeId") Long storeId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    /** Totals by reason for the report headline: {@code [reason, lines, netUnits]}. */
    @Query("""
            SELECT a.reason, COUNT(a), SUM(a.quantityDelta)
            FROM StockAdjustment a
            WHERE (:storeId IS NULL OR a.store.id = :storeId)
              AND (:from IS NULL OR a.postedAt >= :from)
              AND (:to IS NULL OR a.postedAt <= :to)
            GROUP BY a.reason
            """)
    List<Object[]> summariseByReason(@Param("storeId") Long storeId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);
}
