package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request line for creating a {@link Movement}. Set exactly one of assetId / commodityId. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementItemDTO {
    private Long assetId;
    private Long commodityId;
    /** Units moved; defaults to 1 (required for a consumable line). */
    private Integer quantity;
}
