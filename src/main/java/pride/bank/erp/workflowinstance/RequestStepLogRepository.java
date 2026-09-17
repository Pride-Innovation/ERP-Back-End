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

    /**
     * Whether the workflow has ever involved this person in this request.
     *
     * <p>An existence check rather than loading the trail: this is asked on a single-record read, and
     * the answer is a boolean.
     *
     * <p>The dotted path through {@code actor} is an inner join and should be — a step routed to a
     * group mailbox has no actor and can never match a named one, so there is nothing optional here
     * for trap #1 to catch.
     */
    @Query("SELECT COUNT(l) > 0 FROM RequestStepLog l "
            + "WHERE l.instance.request.id = :requestId AND l.actor.id = :actorId")
    boolean existsByRequestIdAndActorId(@Param("requestId") Long requestId,
                                        @Param("actorId") Long actorId);
}
