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

    /**
     * Why an asset is being written off before it has served its category's useful life — damage,
     * theft, irreparable fault. Required in that case and ignored otherwise: an early write-off is
     * legitimate but should never be indistinguishable from an end-of-life disposal in the audit
     * trail.
     */
    private String earlyDisposalReason;
}
