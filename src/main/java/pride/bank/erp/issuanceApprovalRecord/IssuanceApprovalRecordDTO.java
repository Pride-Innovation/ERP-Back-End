package pride.bank.erp.issuanceApprovalRecord;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssuanceApprovalRecordDTO {
    private Long issuanceId;
    private String comment;
    private Long requestId;
    private Long statusId;
}
