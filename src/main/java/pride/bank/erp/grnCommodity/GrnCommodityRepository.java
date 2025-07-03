package pride.bank.erp.grnCommodity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GrnCommodityRepository extends JpaRepository<GrnCommodity, Long> {
}
