package pride.bank.erp.grnCommodity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrnCommodityRepository extends JpaRepository<GrnCommodity, Long> {
    @Query("SELECT gc FROM GrnCommodity gc JOIN FETCH gc.grnReport gr JOIN FETCH gr.stock WHERE gr.stock.id = :stockId")
    Page<GrnCommodity> findByStockId(@Param("stockId") Long stockId, Pageable pageable);

    List<GrnCommodity> findByGrnReportId(Long id);

    /**
     * Quantity documented by the GRN trail, per order line: {@code [stockId, commodityId, qty]}.
     * Compared against the line's delivered quantity, the difference is the number of units that
     * entered the order without a receipt behind them — and so were never credited to a store.
     */
    @Query("""
            SELECT gc.grnReport.stock.id, gc.commodity.id, SUM(gc.deliveredQuantity)
            FROM GrnCommodity gc
            WHERE gc.disposed = false AND gc.grnReport.disposed = false
            GROUP BY gc.grnReport.stock.id, gc.commodity.id
            """)
    List<Object[]> sumReceivedQuantityByStockAndCommodity();
}
