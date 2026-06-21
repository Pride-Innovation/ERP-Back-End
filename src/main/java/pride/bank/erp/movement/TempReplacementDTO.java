package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Issues a temporary replacement asset from the IT store to a user while a repair runs (§15). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TempReplacementDTO {
    private Long tempAssetId;
    private Long recipientUserId;
    private String courierService;
    private String trackingNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    private List<String> deliveryDocuments;
    private String remarks;
}
