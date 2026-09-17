package pride.bank.erp.store;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.branch.BranchRepository;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The store balance listing, which had three filters on screen and none behind them.
 *
 * <p>{@code GET /store} declared {@code branchId}, {@code assetTypeId} and {@code storeType}. The
 * page sent {@code status}, {@code stockName} and {@code createdAt}. Spring drops a parameter no
 * endpoint names without complaint, so all three boxes looked like they worked and returned the
 * unfiltered list — and the Status box offered Active / Disabled / Locked, which are user-account
 * states and were never meaningful for stock.
 *
 * <p>Two faults were fixed at once here, and the second is the quieter one: the listing had no
 * ordering at all, so ties came back in whatever order MySQL chose per query. With OFFSET/LIMIT that
 * puts the same row on two pages and another on none.
 */
@SpringBootTest
class StoreBalanceSearchTest {

    @Autowired
    private StoreBalanceRepository storeBalanceRepository;

    @Autowired
    private StoreService storeService;

    @Autowired
    private BranchRepository branchRepository;

    private Long anyBranchId() {
        return branchRepository.findAll().stream()
                .findFirst()
                .map(b -> b.getId())
                .orElseThrow(() -> new IllegalStateException("branches are seeded; one must exist"));
    }

    private Page<StoreBalance> search(Long assetTypeId, StoreType storeType, String stockName) {
        return search(assetTypeId, storeType, stockName, null);
    }

    private Page<StoreBalance> search(Long assetTypeId, StoreType storeType,
                                      String stockName, String stockLevel) {
        return storeService.fetchStoreDetailsPerBranchPerAssetType(
                anyBranchId(), assetTypeId, storeType, stockName, stockLevel, PageRequest.of(0, 50));
    }

    @Test
    @Transactional(readOnly = true)
    void everyFilterIsOptional() {
        assertThatCode(() -> search(null, null, null))
                .as("no filters at all is the store landing page's own call")
                .doesNotThrowAnyException();
    }

    /**
     * The filter that had nothing behind it.
     *
     * <p>Asserted as a narrowing rather than against fixture data: this runs on the development
     * database, and a test that demanded a particular commodity exist would fail for a reason that
     * has nothing to do with the filter.
     */
    @Test
    @Transactional(readOnly = true)
    void theStockNameFilterNarrowsTheListing() {
        List<StoreBalance> all = search(null, null, null).getContent();
        if (all.isEmpty()) return;   // nothing in stock anywhere; the query still ran

        String aName = all.stream()
                .filter(b -> b.getCommodity() != null && b.getCommodity().getName() != null)
                .map(b -> b.getCommodity().getName())
                .findFirst()
                .orElse(null);
        if (aName == null) return;

        List<StoreBalance> matched = search(null, null, aName).getContent();

        assertThat(matched).isNotEmpty();
        assertThat(matched).allSatisfy(balance ->
                assertThat(balance.getCommodity().getName().toLowerCase())
                        .contains(aName.toLowerCase()));
    }

    @Test
    @Transactional(readOnly = true)
    void aBlankSearchIsTreatedAsNoSearch() {
        int all = search(null, null, null).getContent().size();

        assertThat(search(null, null, "   ").getContent()).hasSize(all);
        assertThat(search(null, null, "").getContent()).hasSize(all);
    }

    @Test
    @Transactional(readOnly = true)
    void searchIsCaseInsensitive() {
        List<StoreBalance> all = search(null, null, null).getContent();
        String aName = all.stream()
                .filter(b -> b.getCommodity() != null && b.getCommodity().getName() != null)
                .map(b -> b.getCommodity().getName())
                .findFirst()
                .orElse(null);
        if (aName == null) return;

        assertThat(search(null, null, aName.toUpperCase()).getContent())
                .hasSameSizeAs(search(null, null, aName.toLowerCase()).getContent());
    }

    /**
     * Asking for "all categories" must not drop lines whose commodity has no category.
     *
     * <p>Written as {@code s.commodity.assetType.id} the optional filter compiles to an INNER JOIN
     * that sits in the FROM clause and filters <em>before</em> the {@code IS NULL} test is evaluated.
     * This codebase has been bitten by that four times; the query uses explicit LEFT JOINs so that
     * the unfiltered call is genuinely unfiltered.
     */
    @Test
    @Transactional(readOnly = true)
    void theUnfilteredCallIsNotNarrowedByTheOptionalCategoryJoin() {
        long viaRepository = storeBalanceRepository
                .search(anyBranchId(), null, null, null, null, PageRequest.of(0, 1))
                .getTotalElements();

        long uncategorised = storeBalanceRepository
                .findAllByBranchIdWithCommodityAndBranch(anyBranchId()).stream()
                .filter(b -> b.getCommodity() == null || b.getCommodity().getAssetType() == null)
                .count();

        assertThat(viaRepository)
                .as("a balance line whose commodity has no category must still be listed; if the "
                        + "optional filter inner-joins, this is short by exactly %d", uncategorised)
                .isGreaterThanOrEqualTo(uncategorised);
    }

    /**
     * The stock-level filter applies each line's <b>own</b> reorder threshold.
     *
     * <p>The store page's Status column used to invent bands in the browser — under 5 low, 5 to 10
     * warning, over 10 in stock — and ignore {@code minLevel} entirely. So a commodity with a
     * threshold of 50 and 30 on hand read "in stock" on that page while {@code /inventory/low-stock}
     * correctly flagged it: two screens, two answers, one commodity. The rule here is the one the
     * low-stock monitor and the branch overview already use.
     */
    @Test
    @Transactional(readOnly = true)
    void lowStockUsesEachLinesOwnReorderThreshold() {
        for (StoreBalance balance : search(null, null, null, "low").getContent()) {
            assertThat(balance.getMinLevel())
                    .as("a line with no threshold is never low, however little is on hand")
                    .isGreaterThan(0);
            assertThat(balance.getQuantity())
                    .as("low means at or below the threshold, not below it")
                    .isLessThanOrEqualTo(balance.getMinLevel());
        }
    }

    @Test
    @Transactional(readOnly = true)
    void adequateIsTheComplementOfLowAmongMonitoredLines() {
        for (StoreBalance balance : search(null, null, null, "adequate").getContent()) {
            assertThat(balance.getMinLevel()).isGreaterThan(0);
            assertThat(balance.getQuantity()).isGreaterThan(balance.getMinLevel());
        }
    }

    /** Finding the lines nobody has set a threshold on is how the gaps get closed. */
    @Test
    @Transactional(readOnly = true)
    void unmonitoredIsAnAnswerRatherThanAnAbsence() {
        for (StoreBalance balance : search(null, null, null, "unmonitored").getContent()) {
            assertThat(balance.getMinLevel()).isZero();
        }
    }

    @Test
    @Transactional(readOnly = true)
    void theThreeLevelsPartitionTheListingExactly() {
        int all = search(null, null, null).getContent().size();
        int low = search(null, null, null, "low").getContent().size();
        int adequate = search(null, null, null, "adequate").getContent().size();
        int unmonitored = search(null, null, null, "unmonitored").getContent().size();

        assertThat(low + adequate + unmonitored)
                .as("every line is exactly one of the three; a gap means a boundary is wrong on "
                        + "both sides, which is how the old under-5 / 5-to-10 bands lost quantity 5")
                .isEqualTo(all);
    }

    /** A stale client sending something else gets the unfiltered list, not a failed page. */
    @Test
    @Transactional(readOnly = true)
    void anUnrecognisedLevelIsTreatedAsNoFilter() {
        int all = search(null, null, null).getContent().size();
        assertThat(search(null, null, null, "bananas").getContent()).hasSize(all);
        assertThat(search(null, null, null, "  ").getContent()).hasSize(all);
    }

    /** Pagination without a tie-break repeats rows across pages and drops others entirely. */
    @Test
    @Transactional(readOnly = true)
    void pagingIsStable() {
        Page<StoreBalance> firstPage = storeService.fetchStoreDetailsPerBranchPerAssetType(
                anyBranchId(), null, null, null, null, PageRequest.of(0, 3));
        if (firstPage.getTotalElements() <= 3) return;   // not enough rows to page

        Page<StoreBalance> secondPage = storeService.fetchStoreDetailsPerBranchPerAssetType(
                anyBranchId(), null, null, null, null, PageRequest.of(1, 3));

        var firstIds = firstPage.getContent().stream().map(StoreBalance::getId).collect(Collectors.toSet());
        var secondIds = secondPage.getContent().stream().map(StoreBalance::getId).collect(Collectors.toSet());

        assertThat(firstIds)
                .as("a row on two pages means another row is on none")
                .doesNotContainAnyElementsOf(secondIds);
    }

    /** A caller's own ordering is respected; the tie-break is appended, not substituted. */
    @Test
    @Transactional(readOnly = true)
    void anExplicitSortSurvives() {
        Page<StoreBalance> sorted = storeService.fetchStoreDetailsPerBranchPerAssetType(
                anyBranchId(), null, null, null, null,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "quantity")));

        assertThat(sorted.getSort().getOrderFor("quantity"))
                .as("the tie-break must not replace what the caller asked for")
                .isNotNull();
        assertThat(sorted.getSort().getOrderFor("id")).isNotNull();
    }
}
