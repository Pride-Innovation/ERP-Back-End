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
    private String trackingNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    private List<String> deliveryDocuments;
}
