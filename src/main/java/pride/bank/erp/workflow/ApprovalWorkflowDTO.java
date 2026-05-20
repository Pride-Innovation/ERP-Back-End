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
    private Long assetTypeId;
    private String assetTypeName;
    private Long branchId;
    private String branchName;
    private WorkflowBranchScope branchScope;
    private boolean active;
    private int priority;

    @Builder.Default
    private List<ApprovalStepDTO> steps = new ArrayList<>();
}
