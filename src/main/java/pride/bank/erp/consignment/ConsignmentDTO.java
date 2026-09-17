package pride.bank.erp.consignment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Opens a journey. Contents are added afterwards, so a consignment can be filled as stock is picked. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsignmentDTO {
    private Long sourceLocationId;
    private Long destLocationId;
    private String remarks;
    /** Optional: load these movements straight away rather than adding them one at a time. */
    private List<Long> movementIds;
}
