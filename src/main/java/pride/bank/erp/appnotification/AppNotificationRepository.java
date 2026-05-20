package pride.bank.erp.appnotification;

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
}
