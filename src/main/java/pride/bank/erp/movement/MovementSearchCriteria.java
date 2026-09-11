package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * What a movement listing is being narrowed by.
 *
 * <h2>Why this exists</h2>
 * The movements page carried an eleven-field filter bar that ran entirely in the browser, over the
 * first hundred rows the page happened to have fetched. Every one of those filters was therefore
 * answering a question about a subset rather than about the register: a movement matching perfectly
 * but sitting at position 101 came back as "no results", which is indistinguishable from "no such
 * movement". {@code GET /movements} declared no filter parameters at all, so there was nothing to
 * wire the bar to — this is that missing half.
 *
 * <p>Every field is optional and they combine with AND, matching what the panel did.
 *
 * <h2>The two fields the client does not send</h2>
 * {@link #scopedBranchId} is filled by the service from {@code AccessScopeService}, never from the
 * request — a client-supplied scope is not a scope. It reproduces {@code MovementRepository#findForBranch}:
 * a movement is in reach if <b>any</b> of its four ends is the caller's branch.
 *
 * @see MovementSearchDao where these become predicates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementSearchCriteria {

    /** Created on or after this instant. */
    private LocalDateTime dateFrom;

    /** Created on or before this instant. */
    private LocalDateTime dateTo;

    private MovementType movementType;
    private MovementCategory movementCategory;
    private MovementStatus status;
    private ReceiptStatus receiptStatus;

    /** Partial, case-insensitive — the panel offers this as a free-text box. */
    private String trackingNumber;

    /** Exact name of the source store, as the panel's dropdown presents it. */
    private String source;

    /**
     * Where it is going, as one label.
     *
     * <p>A movement goes either to a store or to a person, never both, and the page shows whichever
     * applies in a single column. Matching therefore has to consider both — see
     * {@code MovementSearchDao#destinationLabel}.
     */
    private String destination;

    /** Courier name, which may come from the courier record or from the free-text service field. */
    private String courier;

    /** Full name of whoever raised the movement. */
    private String initiator;

    /**
     * The branch the caller may see, or {@code null} for no restriction.
     *
     * <p><b>Server-resolved.</b> Filled from {@code AccessScopeService}, never bound from the
     * request. Set at BRANCH scope; at SELF the narrowing is by person instead — see
     * {@link #scopedUserId}. The two are never both set.
     */
    private Long scopedBranchId;

    /**
     * The person whose own movements to show, or {@code null} for no such restriction.
     *
     * <h2>Why a movement needs this and an asset does not</h2>
     * An asset has {@code assignedTo}; a movement has no owner at all. It has four <em>ends</em> and
     * several person links, so "mine" has to be defined as <b>a movement I am a party to</b>: I
     * raised it, it left my hands, it is coming to me, I signed for it, or it fulfils a request I
     * raised.
     *
     * <p>Without this, SELF collapsed into BRANCH — {@code resolveBranch} returns the caller's branch
     * for anything below ALL — so an officer granted {@code READ_MOVEMENT} saw their whole branch's
     * register rather than their own movements.
     *
     * <p><b>Server-resolved</b>, like the branch above.
     */
    private Long scopedUserId;
}
