package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Sends a faulty asset from its branch to the Head Office IT store for repair (§14). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairTransferDTO {
    private Long assetId;
    private Long repairId;
    private String courierService;
    private String trackingNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    private List<String> deliveryDocuments;
    private String remarks;
}
