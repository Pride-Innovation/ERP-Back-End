package pride.bank.erp.workflowinstance;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class StepLogDTO {
    private Long id;
    private int stepOrder;
    private String stepName;
    /**
     * The step's approver type — BRANCH_MANAGER, DEPT_HEAD, BOM and so on.
     *
     * Exposed because {@code stepName} is free text an administrator can edit, so a client that
     * needs to know *which office* signed off (rather than merely that something was signed off)
     * cannot rely on it. Resolved from the workflow definition at read time; null when the step
     * can no longer be matched, e.g. after the workflow was edited.
     */
    private String approverType;
    /** REQUEST_APPROVAL / ISSUANCE / ACKNOWLEDGE_RECEIPT / ACKNOWLEDGE_REQUEST. Same rationale. */
    private String stepType;
    private String action;
    private String comment;
    private Long actorId;
    private String actorName;
    private String actorEmail;
    private LocalDateTime createDate;
    private LocalDateTime lastModified;
}
