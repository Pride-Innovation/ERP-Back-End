package pride.bank.erp.issuedAsset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssuedAssetRepository extends JpaRepository<IssuedAsset, Long> {
    List<IssuedAsset> findByIssuance_Id(Long issuanceId);
}
