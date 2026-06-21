package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Request body for creating a {@link Movement}. {@code movementCategory} is derived server-side. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementDTO {
    private MovementType movementType;

    private Long sourceStoreId;
    private Long destStoreId;
    private Long recipientUserId;

    // Logistics (used only when the derived category is INTER_LOCATION)
    private String courierService;
    private String trackingNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    private List<String> deliveryDocuments;

    // Optional links
    private Long requestId;
    private Long repairId;

    private String remarks;

    private List<MovementItemDTO> items;
}
