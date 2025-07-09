package pride.bank.erp.acknowledgeIssuanceReceipt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcknowledgeIssuanceReceiptDTO {
    private Long requestId;
    private Long statusId;
    private String comment;
}
