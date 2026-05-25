package pride.bank.erp.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One step of a resolved workflow, including the concrete approver
 * (when a requester is known), fallback email and skip metadata.
 * Matches the frontend's {@code IResolvedApprovalStep}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedApprovalStepDTO {
    private int stepOrder;
    private String stepName;
    private StepType stepType;
    private StepApproverType approverType;

    /** Display name of the resolved approver (null when no requester or no user). */
    private String approverName;

    /** Email of the resolved approver or the configured group email. */
    private String approverEmail;

    /**
     * Optional CC / fallback email. Populated when the approver is dept-managed
     * (uses {@code department.managersGroupEmail}) or otherwise from
     * {@code step.notifyGroupEmail}.
     */
    private String fallbackEmail;

    private boolean skipped;
    private String skipReason;
    private boolean optional;
}
