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

    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
