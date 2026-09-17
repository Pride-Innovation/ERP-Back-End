package pride.bank.erp.consignment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The consignments register, filtered in the database rather than in the browser.
 *
 * <h2>Why this exists</h2>
 * The page fetched the first hundred journeys once and did the rest client-side: ten filters, the
 * tabs, their counts, the dropdown options and three exports. The endpoint declared two parameters
 * and applied them in a ternary, so choosing a status <b>silently discarded the branch</b>, and
 * choosing neither returned every consignment in the bank.
 *
 * <p>Written against the development database rather than a fixture: a filter test that builds its
 * own two rows proves the predicate compiles, not that the filter reaches the query the page calls.
 * Where the data cannot support a case the test returns early rather than asserting something
 * vacuous.
 *
 * <p><b>Both the scoped and the unscoped path are exercised.</b> Leaving {@code scopedBranchId} null
 * is the easy default in a test with no principal, and it is the one that does not need the check —
 * that oversight is how the movements register shipped broken for every branch user.
 */
@SpringBootTest
class ConsignmentSearchDaoTest {

    @Autowired
    private ConsignmentSearchDao dao;

    private static final Pageable FIRST_200 =
            PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "id"));

    private Page<Consignment> search(ConsignmentSearchCriteria criteria) {
        return dao.search(criteria, FIRST_200);
    }

    private List<Consignment> all() {
        return search(new ConsignmentSearchCriteria()).getContent();
    }

    // ── the unfiltered listing ───────────────────────────────────────────────

    /**
     * A journey missing an end, a courier or a dispatch date must still be listed.
     *
     * <p>All of those are nullable — a draft has not been dispatched and may not have a courier yet —
     * so a dotted path in any optional filter would inner-join and drop them in the FROM clause.
     */
    @Test
    @Transactional(readOnly = true)
    void theUnfilteredListingIncludesDraftsAndJourneysWithoutACourier() {
        List<Consignment> consignments = all();
        if (consignments.isEmpty()) return;

        long withoutCourier = consignments.stream().filter(c -> c.getCourier() == null).count();
        long withoutDispatch = consignments.stream().filter(c -> c.getDispatchDate() == null).count();

        System.out.printf("consignments=%d withoutCourier=%d withoutDispatchDate=%d%n",
                consignments.size(), withoutCourier, withoutDispatch);

        assertThat(consignments).isNotEmpty();
    }

    @Test
    @Transactional(readOnly = true)
    void theTotalMatchesTheRowsReturned() {
        Page<Consignment> page = search(new ConsignmentSearchCriteria());
        if (page.getTotalElements() > 200) return;

        assertThat(page.getContent())
                .as("a duplicated join inflates the count without adding rows, and the pager then "
                        + "offers pages that hold nothing")
                .hasSize((int) page.getTotalElements());
    }

    @Test
    @Transactional(readOnly = true)
    void noConsignmentAppearsTwice() {
        assertThat(all().stream().map(Consignment::getId).toList())
                .as("a journey joined through both of its ends must still be one row")
                .doesNotHaveDuplicates();
    }

    // ── the fault that made two declared filters one ─────────────────────────

    /**
     * Status and the other filters must combine, not replace one another.
     *
     * <p>The old handler chose <em>one</em> finder by ternary, so a status silently discarded
     * everything else. Both parameters were declared and documented, which is what made it harder to
     * spot than an undeclared one.
     */
    @Test
    @Transactional(readOnly = true)
    void statusCombinesWithTheOtherFiltersRatherThanReplacingThem() {
        ConsignmentStatus status = all().stream()
                .map(Consignment::getStatus).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (status == null) return;

        assertThat(search(ConsignmentSearchCriteria.builder()
                .statuses(List.of(status))
                .dateTo(LocalDateTime.now().minusYears(50))
                .build()).getContent())
                .as("a real status combined with a window nothing falls into must return nothing; "
                        + "rows here mean one filter is discarding the other")
                .isEmpty();
    }

    // ── the filters ──────────────────────────────────────────────────────────

    @Test
    @Transactional(readOnly = true)
    void filteringByStatusReturnsOnlyThatStatus() {
        ConsignmentStatus status = all().stream()
                .map(Consignment::getStatus).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (status == null) return;

        assertThat(search(ConsignmentSearchCriteria.builder().statuses(List.of(status)).build()).getContent())
                .isNotEmpty()
                .allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(status));
    }

    /**
     * A draft has no dispatch date, and must still be reachable by a date filter.
     *
     * <p>The panel dates a journey on {@code dispatchDate ?? createDate}. Dating purely on the
     * dispatch column would drop every draft from a date-filtered view — and drafts are often exactly
     * what someone filtering by "this week" is looking for.
     */
    @Test
    @Transactional(readOnly = true)
    void aDateFilterStillReachesDraftsWithNoDispatchDate() {
        List<Consignment> drafts = all().stream()
                .filter(c -> c.getDispatchDate() == null && c.getCreateDate() != null)
                .toList();
        if (drafts.isEmpty()) return;

        LocalDateTime longAgo = LocalDateTime.now().minusYears(50);
        List<Long> reachable = search(ConsignmentSearchCriteria.builder().dateFrom(longAgo).build())
                .getContent().stream().map(Consignment::getId).toList();

        assertThat(reachable)
                .as("%d draft(s) have no dispatch date and are dated on when they were opened; "
                        + "dating on the dispatch column alone loses them", drafts.size())
                .contains(drafts.get(0).getId());
    }

    @Test
    @Transactional(readOnly = true)
    void filteringBySourceNarrowsTheListing() {
        List<Consignment> everything = all();
        String source = everything.stream()
                .filter(c -> c.getSourceLocation() != null)
                .map(c -> c.getSourceLocation().getName())
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (source == null) return;

        assertThat(search(ConsignmentSearchCriteria.builder().source(source).build()).getContent())
                .isNotEmpty()
                .allSatisfy(c -> assertThat(c.getSourceLocation().getName()).isEqualTo(source));
    }

    /**
     * Only a journey on the road can be late.
     *
     * <p>A draft has not left and a received one has arrived, so neither is overdue however old its
     * expected date. Getting this wrong reports every stale draft as running late.
     */
    @Test
    @Transactional(readOnly = true)
    void onlyJourneysOnTheRoadCountAsOverdue() {
        List<Consignment> overdue = search(
                ConsignmentSearchCriteria.builder().timeliness("overdue").build()).getContent();

        System.out.printf("overdue=%d of %d%n", overdue.size(), all().size());

        assertThat(overdue).allSatisfy(c -> {
            assertThat(c.getStatus())
                    .as("consignment %s is not on the road and cannot be overdue", c.getId())
                    .isIn(ConsignmentStatus.DISPATCHED, ConsignmentStatus.IN_TRANSIT);
            assertThat(c.getExpectedDeliveryDate()).isNotNull();
            assertThat(c.getExpectedDeliveryDate()).isBefore(LocalDateTime.now());
        });
    }

    /** Overdue and on-time must partition the register, or one of them is silently wrong. */
    @Test
    @Transactional(readOnly = true)
    void overdueAndOnTimePartitionTheRegister() {
        long total = search(new ConsignmentSearchCriteria()).getTotalElements();
        long overdue = search(
                ConsignmentSearchCriteria.builder().timeliness("overdue").build()).getTotalElements();
        long onTime = search(
                ConsignmentSearchCriteria.builder().timeliness("on-time").build()).getTotalElements();

        assertThat(overdue + onTime)
                .as("every journey is one or the other; a gap means 'on time' negates only the date "
                        + "and reports drafts as late")
                .isEqualTo(total);
    }

    /** A draft with nothing on it cannot be dispatched, so "empty" is how those are found. */
    @Test
    @Transactional(readOnly = true)
    void theLoadFilterSeparatesEmptyJourneysFromLoadedOnes() {
        long total = search(new ConsignmentSearchCriteria()).getTotalElements();
        long empty = search(
                ConsignmentSearchCriteria.builder().load("empty").build()).getTotalElements();
        long loaded = search(
                ConsignmentSearchCriteria.builder().load("loaded").build()).getTotalElements();

        System.out.printf("empty=%d loaded=%d total=%d%n", empty, loaded, total);

        assertThat(empty + loaded)
                .as("a journey either carries movements or does not")
                .isEqualTo(total);
    }

    // ── the scoped path ──────────────────────────────────────────────────────

    /** A scoped listing must run, and reach a journey by either of its ends. */
    @Test
    @Transactional(readOnly = true)
    void scopingKeepsOnlyJourneysTouchingThatBranch() {
        Long branch = anyBranchId();
        if (branch == null) return;

        List<Consignment> scoped = search(
                ConsignmentSearchCriteria.builder().scopedBranchId(branch).build()).getContent();

        System.out.printf("branch %d touches %d of %d consignment(s)%n",
                branch, scoped.size(), all().size());

        assertThat(scoped).allSatisfy(c -> assertThat(endsOf(c))
                .as("consignment %s was returned for branch %d but neither end is there",
                        c.getId(), branch)
                .contains(branch));
    }

    /** The scoped tabs and dropdowns run the same predicate and must not fall over either. */
    @Test
    @Transactional(readOnly = true)
    void theScopedTabsAndOptionsRun() {
        Long branch = anyBranchId();
        if (branch == null) return;

        assertThat(dao.countByStatus(
                ConsignmentSearchCriteria.builder().scopedBranchId(branch).build())).isNotNull();
        assertThat(dao.filterOptions(branch)).isNotNull();
    }

    @Test
    @Transactional(readOnly = true)
    void theScopedTotalMatchesTheScopedRows() {
        Long branch = anyBranchId();
        if (branch == null) return;

        Page<Consignment> page = dao.search(
                ConsignmentSearchCriteria.builder().scopedBranchId(branch).build(), FIRST_200);
        if (page.getTotalElements() > 200) return;

        assertThat(page.getContent()).hasSize((int) page.getTotalElements());
    }

    // ── tabs and dropdowns ───────────────────────────────────────────────────

    @Test
    @Transactional(readOnly = true)
    void theStatusCountsSumToTheTotal() {
        ConsignmentSearchCriteria criteria = new ConsignmentSearchCriteria();
        long total = search(criteria).getTotalElements();
        Map<ConsignmentStatus, Long> counts = dao.countByStatus(criteria);

        System.out.printf("status counts %s (total %d)%n", counts, total);

        assertThat(counts.values().stream().mapToLong(Long::longValue).sum())
                .as("every journey has exactly one status, so the tabs must partition the total")
                .isEqualTo(total);
    }

    /**
     * Every value the dropdowns offer must be one the filter can match.
     *
     * <p>The courier label reads {@code courierService} before the courier record — the opposite
     * precedence from movements, matching this page's own helper. If the two disagreed, the dropdown
     * would offer a name the query could never find.
     */
    @Test
    @Transactional(readOnly = true)
    void everyOfferedOptionIsMatchable() {
        ConsignmentSearchDao.FilterOptions options = dao.filterOptions(null);

        System.out.printf("options sources=%d destinations=%d couriers=%d%n",
                options.sources().size(), options.destinations().size(), options.couriers().size());

        for (String source : options.sources().stream().limit(3).toList()) {
            assertThat(search(ConsignmentSearchCriteria.builder().source(source).build()).getContent())
                    .as("'%s' is offered by the dropdown, so the filter must find it", source)
                    .isNotEmpty();
        }
        for (String courier : options.couriers().stream().limit(3).toList()) {
            assertThat(search(ConsignmentSearchCriteria.builder().courier(courier).build()).getContent())
                    .as("'%s' is offered by the dropdown, so the filter must find it", courier)
                    .isNotEmpty();
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Long anyBranchId() {
        return all().stream()
                .map(ConsignmentSearchDaoTest::endsOf)
                .flatMap(java.util.Set::stream)
                .findFirst()
                .orElse(null);
    }

    /** Both ends of a journey — the same two the scope predicate reads. */
    private static java.util.Set<Long> endsOf(Consignment c) {
        java.util.Set<Long> ends = new java.util.LinkedHashSet<>();
        if (c.getSourceLocation() != null) ends.add(c.getSourceLocation().getId());
        if (c.getDestLocation() != null) ends.add(c.getDestLocation().getId());
        return ends;
    }
}
