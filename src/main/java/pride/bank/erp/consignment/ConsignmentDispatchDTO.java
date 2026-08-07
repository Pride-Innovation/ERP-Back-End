package pride.bank.erp.consignment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hands a consignment to a courier. Mirrors the per-movement dispatch payload it replaces, so the
 * same courier picker and the same signed-note requirement apply to a whole van-load at once.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsignmentDispatchDTO {
    /** A vetted courier from the registry. Takes priority over {@link #courierName}. */
    private Long courierId;
    /** Free-text name for an ad-hoc courier — resolved or created by name. */
    private String courierName;
    private String plateNumber;
    private String trackingNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    /** At least one signed dispatch note. Enforced, exactly as it is for a single movement. */
    private List<String> deliveryDocuments;
}
