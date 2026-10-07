package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Courier/logistics details captured when an inter-location movement is dispatched (§9, §18). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementDispatchDTO {
    private String courierService;
    /*
     * No trackingNumber: the system issues it (TrackingNumberService), and a value sent here would be
     * a way to type a duplicate. The courier's own number goes in courierReference.
     */
    /** The courier's waybill or reference, if they gave one. Optional and not held unique. */
    private String courierReference;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    private List<String> deliveryDocuments;

    /** A vetted courier picked from the registry. Takes priority over {@link #courierName}. */
    private Long courierId;
    /** Free-text courier name for an ad-hoc (unvetted) courier — resolved/auto-created by name. */
    private String courierName;
    /** Vehicle plate number, required for vetted and ad-hoc couriers alike. */
    private String plateNumber;
}
