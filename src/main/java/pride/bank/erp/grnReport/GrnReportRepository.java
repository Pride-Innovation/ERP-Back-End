package pride.bank.erp.grnReport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrnReportRepository extends JpaRepository<GrnReport, Long> {
    List<GrnReport> findByStockId(Long stockId);
}
