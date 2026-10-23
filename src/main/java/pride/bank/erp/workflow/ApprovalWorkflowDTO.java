package pride.bank.erp.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalWorkflowDTO {
    private Long id;
    private String name;
    private String description;

    /** Asset category ids this workflow applies to; empty = all categories. */
    @Builder.Default
    private List<Long> assetTypeIds = new ArrayList<>();

    /** Response-only: human-readable category names parallel to {@link #assetTypeIds}. */
    @Builder.Default
    private List<String> assetTypeNames = new ArrayList<>();

    private Long branchId;
    private String branchName;
    private WorkflowBranchScope branchScope;
    /** PERSONAL, RESTOCK or BOTH. Absent on a save means BOTH. */
    private WorkflowPurpose appliesTo;
    private boolean active;
    private int priority;

    /** Requester role ids this workflow applies to; empty = any requester. */
    @Builder.Default
    private List<Long> requesterRoleIds = new ArrayList<>();

    /** Response-only: human-readable role names parallel to {@link #requesterRoleIds}. */
    @Builder.Default
    private List<String> requesterRoleNames = new ArrayList<>();

    @Builder.Default
    private List<ApprovalStepDTO> steps = new ArrayList<>();
}
