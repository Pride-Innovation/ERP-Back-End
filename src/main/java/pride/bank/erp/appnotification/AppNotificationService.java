package pride.bank.erp.appnotification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.user.User;

@Service
@RequiredArgsConstructor
public class AppNotificationService {

    private final AppNotificationRepository repository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    /**
     * Writes a notification and pushes it to the recipient's live session.
     *
     * @param entityType what the notification is about, so the reader can be taken to it. This was a
     *                   frontend URL built in Java; see {@link NotificationTarget} for why it is not
     *                   any more.
     */
    public AppNotification createAndPush(User recipient, String title, String message,
                                         String type, NotificationTarget entityType, Long entityId) {
        AppNotification notification = AppNotification.builder()
                .recipient(recipient)
                .title(title)
                .message(message)
                .type(type)
                .entityType(entityType)
                .entityId(entityId)
                .isRead(false)
                .build();

        AppNotification saved = repository.save(notification);

        // Push real-time notification over WebSocket
        AppNotificationDTO dto = toDTO(saved);
        messagingTemplate.convertAndSendToUser(
                String.valueOf(recipient.getId()),
                "/queue/notifications",
                dto
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<AppNotificationDTO> getNotifications(Long userId, int page, int size) {
        return repository.findByRecipientIdOrderByCreateDateDesc(userId, PageRequest.of(page, size))
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return repository.countByRecipientIdAndIsRead(userId, false);
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        repository.findById(notificationId).ifPresent(n -> {
            if (n.getRecipient().getId().equals(userId)) {
                n.setRead(true);
                repository.save(n);
            }
        });
    }

    @Transactional
    public void markAllRead(Long userId) {
        repository.markAllReadByRecipientId(userId);
    }

    private AppNotificationDTO toDTO(AppNotification n) {
        return AppNotificationDTO.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .entityType(n.getEntityType() == null ? null : n.getEntityType().name())
                .entityId(n.getEntityId())
                .isRead(n.isRead())
                .createDate(n.getCreateDate())
                .build();
    }
}
