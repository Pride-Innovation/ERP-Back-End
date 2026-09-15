package pride.bank.erp.appnotification;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    Page<AppNotification> findByRecipientIdOrderByCreateDateDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndIsRead(Long recipientId, boolean isRead);

    @Modifying
    @Query("UPDATE AppNotification n SET n.isRead = true WHERE n.recipient.id = :recipientId AND n.isRead = false")
    void markAllReadByRecipientId(@Param("recipientId") Long recipientId);

    /**
     * Discards notifications that have been read and are older than the cut-off.
     *
     * <h2>Why only the read ones</h2>
     * An unread notification is outstanding work, however old — a request still waiting on somebody
     * is not less true for having waited three months, and deleting it would take the prompt away
     * while leaving the work. Read ones have served their purpose: what happened is in
     * {@code audit_event}, which is the append-only record and the right place to ask later.
     *
     * <p>Measured when this was added: 760 rows after a few months of use, none yet older than
     * ninety days. So this prunes nothing today — it exists so the table does not become the largest
     * in the database by default, which is what an unbounded per-event log does.
     */
    @Modifying
    @Query("DELETE FROM AppNotification n WHERE n.isRead = true AND n.createDate < :before")
    int deleteReadOlderThan(@Param("before") LocalDateTime before);
}
