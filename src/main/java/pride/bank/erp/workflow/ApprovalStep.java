package pride.bank.erp.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    @Column(nullable = false)
    @Builder.Default
    private StepType stepType = StepType.REQUEST_APPROVAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepApproverType approverType;

    /** Used when approverType = GROUP_EMAIL */
    private String groupEmail;

    /** Used when approverType = SPECIFIC_USER */
    private Long specificUserId;

    /** CC this group email even when an individual approver is assigned */
    private String notifyGroupEmail;

    @Builder.Default
    private boolean optional = false;

    /** Hours before escalation; 0 = no escalation */
    @Builder.Default
    private int escalationHours = 0;
}
