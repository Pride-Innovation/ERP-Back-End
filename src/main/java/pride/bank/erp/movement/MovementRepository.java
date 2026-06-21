package pride.bank.erp.movement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovementRepository extends JpaRepository<Movement, Long> {

    List<Movement> findByRequest_Id(Long requestId);

    List<Movement> findByRepair_Id(Long repairId);

    Page<Movement> findByStatus(MovementStatus status, Pageable pageable);

    Page<Movement> findBySourceStore_Location_Id(Long locationId, Pageable pageable);

    Page<Movement> findByDestStore_Location_Id(Long locationId, Pageable pageable);
}
