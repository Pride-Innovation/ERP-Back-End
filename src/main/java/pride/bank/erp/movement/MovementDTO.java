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

    /** Exactly one of {@code sourceStoreId} / {@code sourceUserId} must be set. */
    private Long sourceStoreId;
    /** The person handing items back — set only for a {@link MovementType#RETURN_TO_STORE}. */
    private Long sourceUserId;
    private Long destStoreId;
    private Long recipientUserId;

    // ── Repair round-trip: where the asset must go back to ───────────────────
    // Set by RepairMovementService at repair initiation from the asset's own assignment. Not
    // intended for clients — a caller-supplied return destination is what made repaired assets
    // land on the wrong person in the first place.
    private Long originHolderId;
    private Long originLocationId;
    /** The asset status a repair transfer records, for its return leg to restore. */
    private String restoreStatusCode;

    // Logistics (used only when the derived category is INTER_LOCATION)
    private String courierService;
    private String trackingNumber;
    private LocalDateTime dispatchDate;
    private LocalDateTime expectedDeliveryDate;
    private List<String> deliveryDocuments;

    // Optional links
    private Long requestId;
    private Long repairId;
    /** External repair vendor (EXTERNAL routing only). */
    private Long consultantId;

    private String remarks;

    /**
     * Whether this movement must be approved (climbing the initiator's ladder) before it can be
     * dispatched. Opt-in: null/false = no approval (current behavior); true = park in DRAFT and
     * route the initiator's reporting ladder. Per-flow defaults (repair, replenishment, etc.) are
     * wired in their own phases so half-built flows are never stranded pending approval.
     */
    private Boolean requiresApproval;

    /**
     * Set once the initiator has been told no approver could be resolved and has confirmed they want
     * to continue anyway.
     *
     * <p>Deliberately a second, explicit call rather than something the first request can assert:
     * the client only learns approval is impossible from the failure, so a payload that arrives
     * already carrying this has not shown the warning to anybody.
     */
    private Boolean proceedWithoutApproval;

    /** Why the movement is proceeding unapproved. Recorded on the trail; required when bypassing. */
    private String bypassReason;

    private List<MovementItemDTO> items;
}
