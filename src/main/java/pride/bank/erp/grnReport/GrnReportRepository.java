package pride.bank.erp.grnReport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GrnReportRepository extends JpaRepository<GrnReport, Long> {
}
