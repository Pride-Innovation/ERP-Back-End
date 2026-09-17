package pride.bank.erp.appnotification;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class AppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    /** e.g. "REQUEST_SUBMITTED", "STEP_APPROVED", "STEP_REJECTED" */
    private String type;

    /**
     * What this notification is about, and which record.
     *
     * <p>These replaced a {@code link} column holding a frontend URL and a {@code requestId} that
     * held a <em>movement</em> id for movement notifications - a field whose name was wrong for 69
     * rows. Together they say the same thing without either fault: the frontend resolves the pair to
     * a route, so Java never has to know one.
     *
     * <p>Nullable, because a notification need not be about a record. One with no target renders as
     * plain text rather than as something to click - which is the honest rendering, and what 744
     * dead links should have been.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 40)
    private NotificationTarget entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createDate;
}
