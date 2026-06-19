package pride.bank.erp.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    /** Selected fulfilment unit for a GROUP_EMAIL step (preferred over groupEmail). */
    private Long unitId;
    /** Display name of the selected unit (response only). */
    private String unitName;
    private String groupEmail;
    private Long specificUserId;
    private String specificUserName;
    private String notifyGroupEmail;
    private NotifyGroupSource notifyGroupSource;
    private boolean optional;
    private int escalationHours;

    /** Targets to email when this step is completed. */
    @Builder.Default
    private Set<NotifyGroupSource> notifyOnCompletion = new HashSet<>();

    /**
     * Role ids that cause this step to be skipped when the requester has one of them.
     * Empty = never skipped.
     */
    @Builder.Default
    private List<Long> skipIfRequesterRoleIds = new ArrayList<>();
}
