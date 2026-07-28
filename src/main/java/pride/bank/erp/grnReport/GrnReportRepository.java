package pride.bank.erp.grnReport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrnReportRepository extends JpaRepository<GrnReport, Long> {
    List<GrnReport> findByStockId(Long stockId);

    /** The single GRN documenting a given physical receipt. */
    Optional<GrnReport> findByStockReceiptId(Long stockReceiptId);
}
