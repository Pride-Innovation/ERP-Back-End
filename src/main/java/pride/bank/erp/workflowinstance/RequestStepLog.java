package pride.bank.erp.workflowinstance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class RequestStepLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false)
    private RequestWorkflowInstance instance;

    @Column(nullable = false)
    private int stepOrder;

    private String stepName;

    /** Null if this is a group-email-only step with no individual approver */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "actor_id")
    private User actor;

    /** Email of the actor or the group email */
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StepLogAction action = StepLogAction.PENDING;

    @Column(columnDefinition = "TEXT")
    private String comment;

    /** When the most recent escalation reminder was sent for this pending step (null = none yet). */
    private LocalDateTime lastReminderAt;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createDate;

    @LastModifiedDate
    private LocalDateTime lastModified;
}
