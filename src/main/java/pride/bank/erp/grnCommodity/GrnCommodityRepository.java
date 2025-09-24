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
}
