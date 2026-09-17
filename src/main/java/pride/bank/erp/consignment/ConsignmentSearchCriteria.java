package pride.bank.erp.consignment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What a consignment listing is being narrowed by.
 *
 * <h2>Why this exists</h2>
 * The consignments page carried a ten-field filter panel that ran entirely in the browser, over the
 * first hundred journeys the page happened to have fetched, while {@code GET /consignments} declared
 * exactly two parameters — and applied them in a ternary chain that made them mutually exclusive, so
 * picking a status silently discarded the branch. A journey matching perfectly but sitting at
 * position 101 came back as "no results", which is indistinguishable from "there is no such
 * consignment".
 *
 * <p>Every field is optional and they combine with AND, matching what the panel did.
 *
 * <h2>The field the client does not send</h2>
 * {@link #scopedBranchId} is filled by the service from {@code AccessScopeService}, never from the
 * request. The listing previously took {@code branchId} straight from the client and fell back to
 * {@code findAll} when it was absent — so every holder of {@code READ_MOVEMENT} read the whole
 * bank's journeys, and could read any single branch's by asking for it.
 *
 * @see ConsignmentSearchDao where these become predicates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsignmentSearchCriteria {

    /**
     * Dated on the journey where there is one, else on when it was opened.
     *
     * <p>The panel reads {@code dispatchDate ?? createDate}, and the query has to agree: a draft has
     * no dispatch date, and dating it on nothing would drop every draft from a date-filtered view.
     */
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;

    /**
     * The statuses to include, or empty for all.
     *
     * <p>A list rather than one value because the page's "On the road" tab spans two — DISPATCHED and
     * IN_TRANSIT — and a single-valued parameter would have forced that tab to stay in the browser,
     * which is the arrangement this whole change exists to end.
     */
    private List<ConsignmentStatus> statuses;

    /** Exact names, as the panel's dropdowns present them. */
    private String source;
    private String destination;

    /** Courier name, which may come from the free-text service field or the courier record. */
    private String courier;

    /** Partial, case-insensitive — both are half-remembered off a docket. */
    private String trackingNumber;
    private String plateNumber;

    /**
     * {@code overdue} or {@code on-time}, judged against the expected delivery date.
     *
     * <p>Only a journey actually on the road can be overdue: a draft has not left and a received one
     * has arrived, so neither is late however old its expected date is.
     */
    private String timeliness;

    /** {@code loaded} or {@code empty} — a draft with nothing on it cannot be dispatched. */
    private String load;

    /**
     * The branch the caller may see, or {@code null} for no restriction.
     *
     * <p><b>Server-resolved.</b> A consignment has two ends and the caller is in reach if either is
     * theirs, mirroring {@code ConsignmentRepository#findForBranch}.
     */
    private Long scopedBranchId;
}
