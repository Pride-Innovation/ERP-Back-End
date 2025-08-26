package pride.bank.erp.repair;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface RepairRepository extends JpaRepository<Repair, Long> {
    Page<Repair> findByAssetId(@Param("assetId") Long assetId, Pageable pageable);
}
