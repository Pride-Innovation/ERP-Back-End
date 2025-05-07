package pride.bank.erp.requestReport;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestReportDTO {
    private Long requestId;
    private Long approverId;
    private Long statusId;
    private String comment;
}
