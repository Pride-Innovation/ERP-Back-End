package pride.bank.erp.movement;

import lombok.Builder;
import lombok.Data;

/**
 * What a repair-flow form can state before anything is submitted.
 *
 * <p>Exists so the modal stops guessing. Two things were previously derived on the client and could
 * disagree with the server: whether a movement crosses a location boundary (which decides if courier
 * details are asked for at all), and where a repaired asset is going. The second was worse than a
 * guess — the form read {@code asset.assignedTo}, but a repair transfer clears the assignment the
 * moment the branch admin takes custody, so the field it was reading is empty by design.
 *
 * <p>Every field here is display-only. None of it is submitted back: the server re-derives it on
 * write from the same code path that fills this in.
 */
@Data
@Builder
public class MovementFlowPreview {

    /** Where the items leave from. */
    private String sourceStoreName;
    private String sourceLocationName;

    /** Where they are going — a store, or a person's branch. */
    private String destinationStoreName;
    private String destinationLocationName;

    /**
     * True when source and destination sit in different locations. The single input to whether the
     * form asks for courier, tracking number, dispatch date and expected delivery: within one
     * location there is no journey to track.
     */
    private boolean interLocation;

    // ── Return-after-repair only ─────────────────────────────────────────────

    /** The user the asset goes back to; null when it returns to the branch unassigned. */
    private Long recipientUserId;
    private String recipientUserName;

    /**
     * True when the recorded holder has left the bank, so the asset returns to its origin branch
     * unassigned and flagged as pool stock rather than being handed to someone who is gone.
     */
    private boolean returningToBranchUnassigned;

    /** The loaner that should come back with this return, derived from the user's open issue. */
    private Long loanerAssetId;
    private String loanerAssetLabel;

    /**
     * Set when the flow cannot proceed as configured — no recorded holder, no branch on record.
     * Carried as a message rather than thrown so the form can show it beside the field instead of
     * failing at submit.
     */
    private String blockedReason;
}
