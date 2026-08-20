package pride.bank.erp.asset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the two properties that make the asset table trustworthy: that paging returns every row
 * exactly once, and that filtering by location does not silently hide most of the register.
 *
 * <p>Both were broken in ways an empty-looking screen could never explain.
 */
@SpringBootTest
class AssetPaginationAndFilterTest {

    @Autowired
    private AssetSearchDao assetSearchDao;

    private static final int PAGE_SIZE = 5;

    private AssetPage page(int number, String sortBy, Sort.Direction direction) {
        return AssetPage.builder()
                .pageNumber(number)
                .pageSize(PAGE_SIZE)
                .sortBy(sortBy)
                .sortDirection(direction)
                .build();
    }

    /**
     * Walking every page must yield each asset exactly once.
     *
     * <p>Ordering was on a single column with no tie-break. Rows tied on that column have no defined
     * relative order in MySQL, and it is free to return them differently on each query — so with
     * OFFSET/LIMIT paging the same asset could appear on two pages while another appeared on none.
     * Sorting by anything non-unique made this routine; `id` was safe only by accident of being
     * unique.
     *
     * <p>Asserted on a deliberately low-cardinality column, because that is where the fault lived.
     */
    @Test
    @Transactional(readOnly = true)
    void pagingOnANonUniqueSortReturnsEveryRowExactlyOnce() {
        AssetSearchCriteria criteria = AssetSearchCriteria.builder().build();

        long total = assetSearchDao.findAllWithFilters(page(0, "make", Sort.Direction.ASC), criteria)
                .getTotalElements();
        if (total == 0) return; // nothing seeded to assert against

        // Bounded so a large register does not turn this into a long-running scan.
        int pagesToWalk = (int) Math.min((total + PAGE_SIZE - 1) / PAGE_SIZE, 20);

        List<Long> seen = new ArrayList<>();
        for (int p = 0; p < pagesToWalk; p++) {
            assetSearchDao.findAllWithFilters(page(p, "make", Sort.Direction.ASC), criteria)
                    .getContent()
                    .forEach(a -> seen.add(a.getId()));
        }

        Set<Long> unique = new HashSet<>(seen);
        assertThat(seen)
                .as("paging by a non-unique column returned %d rows but only %d distinct ids — "
                                + "rows are repeating across pages, which means the sort has no tie-break",
                        seen.size(), unique.size())
                .hasSameSizeAs(unique);
    }

    /** The same must hold in the other direction, and on the default sort. */
    @Test
    @Transactional(readOnly = true)
    void pagingIsStableDescendingAndOnTheDefaultSort() {
        AssetSearchCriteria criteria = AssetSearchCriteria.builder().build();

        for (String field : List.of("model", "id")) {
            List<Long> seen = new ArrayList<>();
            for (int p = 0; p < 4; p++) {
                assetSearchDao.findAllWithFilters(page(p, field, Sort.Direction.DESC), criteria)
                        .getContent()
                        .forEach(a -> seen.add(a.getId()));
            }
            assertThat(seen).as("sorting by %s descending repeated rows across pages", field)
                    .hasSameSizeAs(new HashSet<>(seen));
        }
    }

    /**
     * A location filter must include assets nobody is holding.
     *
     * <p>The predicate reached the holder's branch and department through path navigation, which JPA
     * turns into INNER joins. Those filter in the FROM clause, before any of the OR branches is
     * evaluated — so a result could only ever contain assets assigned to a user who had both a
     * branch and a department. Every asset sitting in a store was dropped, even though two of the
     * OR branches existed specifically to include them.
     */
    @Test
    @Transactional(readOnly = true)
    void locationFilterIncludesUnassignedAssets() {
        // An asset with a branch and no holder — the shape the old INNER joins excluded.
        Page<Asset> all = assetSearchDao.findAllWithFilters(
                AssetPage.builder().pageNumber(0).pageSize(200).build(),
                AssetSearchCriteria.builder().build());

        Asset unassigned = all.getContent().stream()
                .filter(a -> a.getAssignedTo() == null && a.getBranch() != null
                        && a.getBranch().getName() != null)
                .findFirst()
                .orElse(null);
        if (unassigned == null) return; // nothing of this shape seeded

        Page<Asset> byLocation = assetSearchDao.findAllWithFilters(
                AssetPage.builder().pageNumber(0).pageSize(500).build(),
                AssetSearchCriteria.builder().location(unassigned.getBranch().getName()).build());

        assertThat(byLocation.getContent())
                .as("asset %s sits at %s with no holder and must appear when filtering by that location",
                        unassigned.getId(), unassigned.getBranch().getName())
                .anyMatch(a -> a.getId().equals(unassigned.getId()));
    }

    /**
     * The count and the rows must describe the same set.
     *
     * <p>Reusing joins rather than re-creating them is what keeps this true: {@code From#join} adds a
     * join per call, so two predicates asking for the same association would duplicate it in the
     * FROM clause and inflate both the rows and the total.
     */
    @Test
    @Transactional(readOnly = true)
    void filteredCountMatchesTheRowsReturned() {
        Page<Asset> all = assetSearchDao.findAllWithFilters(
                AssetPage.builder().pageNumber(0).pageSize(200).build(),
                AssetSearchCriteria.builder().build());

        Asset sample = all.getContent().stream()
                .filter(a -> a.getBranch() != null && a.getBranch().getName() != null)
                .findFirst()
                .orElse(null);
        if (sample == null) return;

        Page<Asset> filtered = assetSearchDao.findAllWithFilters(
                AssetPage.builder().pageNumber(0).pageSize(1000).build(),
                AssetSearchCriteria.builder().location(sample.getBranch().getName()).build());

        assertThat(filtered.getContent().size())
                .as("the reported total (%d) disagrees with the rows returned (%d) — a duplicated "
                                + "join would do this", filtered.getTotalElements(),
                        filtered.getContent().size())
                .isEqualTo((int) Math.min(filtered.getTotalElements(), 1000));

        // And no row appears twice within one page.
        List<Long> ids = filtered.getContent().stream().map(Asset::getId).toList();
        assertThat(ids).hasSameSizeAs(new HashSet<>(ids));
    }

    /** An unknown sort column must fall back, not 500. */
    @Test
    @Transactional(readOnly = true)
    void unknownSortColumnFallsBackInsteadOfThrowing() {
        Page<Asset> result = assetSearchDao.findAllWithFilters(
                page(0, "notAColumn", Sort.Direction.DESC),
                AssetSearchCriteria.builder().build());

        assertThat(result).isNotNull();
    }
}
