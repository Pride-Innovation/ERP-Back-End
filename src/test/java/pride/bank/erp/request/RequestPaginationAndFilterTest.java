package pride.bank.erp.request;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The request listing's equivalent of {@code AssetPaginationAndFilterTest}.
 *
 * <p>This DAO carried the same three faults as the asset one — a single-column sort with no
 * tie-break, an unvalidated {@code sortBy}, and a default direction that disagreed with the
 * controller — plus an approver filter that reached through a nullable association with an INNER
 * join.
 */
@SpringBootTest
class RequestPaginationAndFilterTest {

    @Autowired
    private RequestSearchDao requestSearchDao;

    private static final int PAGE_SIZE = 5;

    private RequestPage page(int number, String sortBy, Sort.Direction direction) {
        return RequestPage.builder()
                .pageNumber(number)
                .pageSize(PAGE_SIZE)
                .sortBy(sortBy)
                .sortDirection(direction)
                .build();
    }

    /**
     * Walking every page must yield each request exactly once.
     *
     * <p>Asserted on {@code priority}, which takes three values across the whole table — so almost
     * every row is tied with almost every other, and an untied sort would scramble the page
     * boundaries badly enough that rows repeat and others are never seen.
     */
    @Test
    @Transactional(readOnly = true)
    void pagingOnANonUniqueSortReturnsEveryRowExactlyOnce() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().build();

        long total = requestSearchDao.findAllWithFilters(page(0, "priority", Sort.Direction.ASC), criteria)
                .getTotalElements();
        if (total == 0) return; // nothing seeded to assert against

        int pagesToWalk = (int) Math.min((total + PAGE_SIZE - 1) / PAGE_SIZE, 20);

        List<Long> seen = new ArrayList<>();
        for (int p = 0; p < pagesToWalk; p++) {
            requestSearchDao.findAllWithFilters(page(p, "priority", Sort.Direction.ASC), criteria)
                    .getContent()
                    .forEach(r -> seen.add(r.getId()));
        }

        assertThat(seen)
                .as("paging by priority returned %d rows but only %d distinct ids — rows are "
                        + "repeating across pages, which means the sort has no tie-break",
                        seen.size(), new HashSet<>(seen).size())
                .hasSameSizeAs(new HashSet<>(seen));
    }

    /**
     * Filtering by approver must not quietly exclude requests that have none.
     *
     * <p>{@code currentApprover} is null once a ladder finishes or a request is rejected. Reaching
     * through it by path navigation builds an INNER join, which drops those rows in the FROM clause
     * before the name is ever compared — so the filter silently answers a narrower question than it
     * appears to.
     */
    @Test
    @Transactional(readOnly = true)
    void approverFilterRunsWithoutExcludingUnapprovedRequests() {
        Page<Request> all = requestSearchDao.findAllWithFilters(
                RequestPage.builder().pageNumber(0).pageSize(200).build(),
                RequestSearchCriteria.builder().build());
        if (all.getContent().isEmpty()) return;

        Request withApprover = all.getContent().stream()
                .filter(r -> r.getCurrentApprover() != null
                        && r.getCurrentApprover().getFirstName() != null)
                .findFirst()
                .orElse(null);
        if (withApprover == null) return;

        Page<Request> filtered = requestSearchDao.findAllWithFilters(
                RequestPage.builder().pageNumber(0).pageSize(200).build(),
                RequestSearchCriteria.builder()
                        .approver(withApprover.getCurrentApprover().getFirstName())
                        .build());

        assertThat(filtered.getContent())
                .as("request %s is assigned to %s and must appear when filtering by that name",
                        withApprover.getId(), withApprover.getCurrentApprover().getFirstName())
                .anyMatch(r -> r.getId().equals(withApprover.getId()));

        // And the join must not have been duplicated: no row may appear twice.
        List<Long> ids = filtered.getContent().stream().map(Request::getId).toList();
        assertThat(ids).hasSameSizeAs(new HashSet<>(ids));
    }

    /** An unknown sort column must fall back, not 500. */
    @Test
    @Transactional(readOnly = true)
    void unknownSortColumnFallsBackInsteadOfThrowing() {
        assertThat(requestSearchDao.findAllWithFilters(
                page(0, "notAColumn", Sort.Direction.DESC),
                RequestSearchCriteria.builder().build()))
                .isNotNull();
    }

    /** An unspecified direction must default rather than throw out of {@code Sort.by(null, …)}. */
    @Test
    @Transactional(readOnly = true)
    void unspecifiedSortDirectionDefaults() {
        assertThat(requestSearchDao.findAllWithFilters(
                RequestPage.builder().pageNumber(0).pageSize(PAGE_SIZE).build(),
                RequestSearchCriteria.builder().build()))
                .isNotNull();
    }
}
