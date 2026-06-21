package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Receipt details captured when a movement is received by the destination officer (§18). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementReceiveDTO {
    /** Defaults to RECEIVED_OK when omitted. */
    private ReceiptStatus receiptStatus;
    private String remarks;
}
