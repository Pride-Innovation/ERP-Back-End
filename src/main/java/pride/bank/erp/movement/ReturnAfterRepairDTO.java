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
    /**
     * Optional. Left null, the destination is derived from the holder recorded when the asset was
     * sent for repair — which is the whole point, so that a repaired asset cannot be returned to the
     * wrong branch by a mis-click.
     */
    private Long destLocationId;
    /** Optional; derived from the recorded holder when omitted. */
    private Long recipientUserId;
    /** Required when returning the asset to someone other than its recorded holder. */
    private String overrideReason;
    /** When set, a second movement returns this temporary asset to the IT store. */
    private Long tempAssetId;
    private String courierService;
    private String trackingNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    private List<String> deliveryDocuments;
    private String remarks;
}
