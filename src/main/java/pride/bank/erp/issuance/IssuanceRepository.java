package pride.bank.erp.issuance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IssuanceRepository extends JpaRepository<Issuance, Long> {
    Optional<Issuance> findByRequestId(Long requestId);
}
