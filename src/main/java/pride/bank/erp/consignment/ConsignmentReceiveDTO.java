package pride.bank.erp.consignment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.movement.ReceiptStatus;

import java.util.List;

/**
 * Hands over some or all of a landed consignment in one action.
 *
 * <p>Receiving movement by movement is the accurate model — each has its own recipient signing for
 * their own items — but it is punishing when a van drops ten of them at once. This lets a branch
 * clear the whole load in a single action while still recording each hand-over individually.
 *
 * <p>The list is <b>explicit rather than implied</b>. Receiving a movement assigns assets to a named
 * person and draws stock out of the branch store; "everything, unless stated otherwise" is the wrong
 * default for that. Anyone who has not collected yet is simply left off the list and stays
 * outstanding.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsignmentReceiveDTO {

    /** Applies to the hand-over as a whole; each line may still carry its own note. */
    private String remarks;

    /** Exactly which movements are being handed over now. Required, and must not be empty. */
    private List<Line> movements;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private Long movementId;
        /** Defaults to RECEIVED_OK when omitted. */
        private ReceiptStatus receiptStatus;
        /** What was wrong — expected whenever the status is not RECEIVED_OK. */
        private String remarks;
    }
}
