package pride.bank.erp.stock;

import lombok.*;

import java.time.LocalDateTime;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSearchCriteria {
    /**
     * The branch whose stock this caller may see.
     *
     * <p>Applied from the caller's permissions, never from the request, so no filter the client
     * sends can widen it. Null means no restriction — ALL scope, which is what a unit confers.
     *
     * <p>Floored at BRANCH rather than SELF: a stock receipt belongs to a branch but to no
     * individual, so narrowing to the person would show a storekeeper nothing at all.
     */
    private Long scopedBranchId;

    private String lpoNumber;

    /**
     * The supplier this stock was bought from.
     *
     * <p>An id rather than a name. The page used to send a partial name that this endpoint never
     * declared, so the filter looked like it worked and returned the unfiltered list — and even had
     * it been wired, a name match makes the user guess at spellings and folds two similarly-named
     * suppliers into one filter.
     */
    private Long supplierId;
    private Long stockStatusId;
    private Boolean disposed;

    /** When the order was <b>created</b> - what the inventory page's "created" range filters on. */
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    /**
     * When the order was <b>placed</b>: its order date, else - 15 orders have none - when it was
     * created. The report's Date column shows exactly this, so the period and the column agree;
     * filtering it on the created date showed rows dated outside the period they were listed under.
     */
    private LocalDateTime orderedFrom;
    private LocalDateTime orderedTo;

    /**
     * Orders with at least one line in this asset category. A category belongs to the commodity on a
     * line, not to the order, so an order spanning two categories matches either.
     */
    private Long assetTypeId;
}
