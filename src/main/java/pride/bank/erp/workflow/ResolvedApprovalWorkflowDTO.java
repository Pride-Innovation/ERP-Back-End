package pride.bank.erp.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolved view of which workflow will run for a given (category, branch, requester) tuple
 * and which approvers will be notified at each step. Matches the frontend's
 * {@code IResolvedApprovalWorkflow}.
 *
 * <p>When no workflow matches, {@link #workflowId} is null, {@link #resolvedSteps} is empty,
 * and {@link #noMatchReason} carries a human-readable explanation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedApprovalWorkflowDTO {
    private Long workflowId;
    private String workflowName;

    /** Email of the team that fulfils the request after approvals (from the matched category). */
    private String fulfilmentGroupEmail;

    private String noMatchReason;

    @Builder.Default
    private List<ResolvedApprovalStepDTO> resolvedSteps = new ArrayList<>();
}
