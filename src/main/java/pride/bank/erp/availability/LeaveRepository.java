package pride.bank.erp.availability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface LeaveRepository extends JpaRepository<Leave, Long> {

    @Query("SELECT l FROM leave_request l " +
            "WHERE l.onLeaveUser.id = :userId " +
            "AND l.active = true " +
            "AND l.endDate <= :now")
    Optional<Leave> findActiveLeaveEndedByUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
