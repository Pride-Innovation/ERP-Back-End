package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Returns a repaired asset to its originating location (§16). Optionally reassigns it to a user, and
 * optionally returns the temporary replacement asset to the IT store in a second movement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnAfterRepairDTO {
    private Long assetId;
    private Long destLocationId;
    private Long recipientUserId;
    /** When set, a second movement returns this temporary asset to the IT store. */
    private Long tempAssetId;
    private String courierService;
    private String trackingNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    private List<String> deliveryDocuments;
    private String remarks;
}
