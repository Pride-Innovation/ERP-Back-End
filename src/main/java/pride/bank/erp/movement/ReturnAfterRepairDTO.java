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
    /**
     * Optional. When set, a second movement returns this temporary asset to its pool store.
     *
     * <p>Normally left null: the loaner is derived from what is actually out with the recipient. It
     * was a manual picker on the form, which made returning a loaner depend on the user remembering
     * to select it — anything forgotten never came back and the pool drained.
     */
    private Long tempAssetId;

    /**
     * Set false when the recipient is keeping the loaner for now, so no return leg is created for
     * it. Null or true derives and returns it.
     */
    private Boolean returnLoaner;
    private String courierService;
    private String trackingNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    private List<String> deliveryDocuments;
    private String remarks;
}
