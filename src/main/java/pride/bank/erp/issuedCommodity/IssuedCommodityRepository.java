package pride.bank.erp.issuedCommodity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssuedCommodityRepository extends JpaRepository<IssuedCommodity, Long> {
}
