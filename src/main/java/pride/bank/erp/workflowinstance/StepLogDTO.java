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
    private String action;
    private String comment;
    private Long actorId;
    private String actorName;
    private String actorEmail;
    private LocalDateTime createDate;
    private LocalDateTime lastModified;
}
