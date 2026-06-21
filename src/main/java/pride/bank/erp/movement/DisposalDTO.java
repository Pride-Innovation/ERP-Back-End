package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Moves an irreparable / written-off asset to the Disposal store (§17). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisposalDTO {
    private Long assetId;
    private String remarks;
}
