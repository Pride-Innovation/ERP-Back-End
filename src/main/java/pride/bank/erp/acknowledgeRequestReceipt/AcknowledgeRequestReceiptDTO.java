package pride.bank.erp.acknowledgeRequestReceipt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcknowledgeRequestReceiptDTO {
    private Long requestId;
    private Long statusId;
    private String comment;
}
