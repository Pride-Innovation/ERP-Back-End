package pride.bank.erp.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalStepDTO {
    private Long id;
    private int stepOrder;
    private String stepName;
    private StepType stepType;
    private StepApproverType approverType;
    private String groupEmail;
    private Long specificUserId;
    private String specificUserName;
    private String notifyGroupEmail;
    private boolean optional;
    private int escalationHours;
}
