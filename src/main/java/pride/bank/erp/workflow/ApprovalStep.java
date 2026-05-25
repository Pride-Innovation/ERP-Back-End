package pride.bank.erp.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.role.Role;

import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    @JsonIgnore
    private ApprovalWorkflow workflow;

    @Column(nullable = false)
    private int stepOrder;

    @Column(nullable = false)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30) NOT NULL")
    @Builder.Default
    private StepType stepType = StepType.REQUEST_APPROVAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30) NOT NULL")
    private StepApproverType approverType;

    /** Used when approverType = GROUP_EMAIL */
    private String groupEmail;

    /** Used when approverType = SPECIFIC_USER */
    private Long specificUserId;

    /** CC this group email even when an individual approver is assigned. Used when {@link #notifyGroupSource} is {@link NotifyGroupSource#STATIC}. */
    private String notifyGroupEmail;

    /** Strategy for resolving the CC group at send time. Defaults to STATIC for legacy rows. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(40) NOT NULL DEFAULT 'STATIC'")
    @Builder.Default
    private NotifyGroupSource notifyGroupSource = NotifyGroupSource.STATIC;

    /**
     * Recipients to notify when this step is completed (approved / acknowledged / issued).
     * Each entry resolves to an email via {@link NotifyGroupSource} — typically used on the
     * final step (e.g. "Acknowledge Receipt") to fan out to the requester's supervisor,
     * admin, etc. {@code STATIC} and {@code NONE} entries are ignored.
     */
    @ElementCollection(targetClass = NotifyGroupSource.class, fetch = FetchType.EAGER)
    @CollectionTable(
            name = "approval_step_notify_on_completion",
            joinColumns = @JoinColumn(name = "step_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "notify_source", nullable = false)
    @Builder.Default
    private Set<NotifyGroupSource> notifyOnCompletion = new HashSet<>();

    @Builder.Default
    private boolean optional = false;

    /** Hours before escalation; 0 = no escalation */
    @Builder.Default
    private int escalationHours = 0;

    /**
     * Roles for which this step should be skipped during workflow resolution.
     * If the requester's effective roles intersect this set, the step is skipped.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "approval_workflow_step_skip_role",
            joinColumns = @JoinColumn(name = "step_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> skipIfRequesterRoles = new HashSet<>();
}
