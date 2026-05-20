package pride.bank.erp.workflowinstance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequestStepLogRepository extends JpaRepository<RequestStepLog, Long> {

    List<RequestStepLog> findByInstanceIdOrderByStepOrderAscCreateDateAsc(Long instanceId);

    @Query("SELECT l FROM RequestStepLog l WHERE l.instance.id = :instanceId AND l.stepOrder = :stepOrder AND l.action = 'PENDING'")
    Optional<RequestStepLog> findPendingByInstanceAndStep(
            @Param("instanceId") Long instanceId,
            @Param("stepOrder") int stepOrder
    );

    @Query("SELECT l FROM RequestStepLog l WHERE l.instance.request.id = :requestId ORDER BY l.stepOrder ASC, l.createDate ASC")
    List<RequestStepLog> findByRequestId(@Param("requestId") Long requestId);
}
