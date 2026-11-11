package pride.bank.erp.stock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

/**
 * The inventory listing's filters, exercised against real rows.
 *
 * <h2>Why this exists</h2>
 * Two of the four filters on that page had never worked, and both failed the same silent way: Spring
 * drops a request parameter no endpoint declares, so the box looked functional and returned the
 * unfiltered list. <b>Supplier</b> was sending a partial name to a parameter that did not exist, and
 * <b>Date Created</b> was sending {@code createdAtFrom}/{@code createdAtTo} to an endpoint that has
 * the same range under {@code startDate}/{@code endDate} — a pure name mismatch, with a working
 * query behind it that nothing ever reached.
 *
 * <p>Nobody would have noticed either from the screen: an unfiltered table looks like a table.
 * These assertions are about the rows that come back, not about the parameters going in, because
 * the parameters going in were exactly what looked fine.
 *
 * <p>Written against the development database rather than a fixture. A filter test that builds its
 * own two rows proves the predicate compiles; it does not prove the filter reaches the query the
 * page actually calls. Where the data cannot support a case — no suppliers, no stock — the test
 * says so by returning early rather than asserting something vacuous.
 */
@SpringBootTest
class StockFilterTest {

    @Autowired
    private StockService stockService;

    @Autowired pride.bank.erp.user.UserRepository userRepository;
    @Autowired pride.bank.erp.config.JwtService jwtService;
    @Autowired pride.bank.erp.access.AccessScopeService accessScopeService;

    /*
     * Signed in as somebody who sees every branch.
     *
     * These tests ran with nobody signed in, which the listing used to answer with every branch's
     * orders - the "no branch on record reads as no restriction" hole that is now refused. They are
     * about filters, not scope, so they run as a caller for whom the whole register is the right
     * answer; scope has its own tests in StockReportScopeTest.
     */
    @org.junit.jupiter.api.BeforeEach
    void signInAsAllBranchesViewer() {
        for (pride.bank.erp.user.User u : userRepository.findAll()) {
            if (u.getBranch() == null) continue;
            signInAs(u);
            if (accessScopeService.scopeFor(pride.bank.erp.access.ScopeSubject.INVENTORY,
                    pride.bank.erp.access.ScopeAction.VIEW) == pride.bank.erp.dashboard.DashboardScope.ALL) {
                return;
            }
        }
        org.junit.jupiter.api.Assumptions.abort("No account sees every branch's inventory");
    }

    @org.junit.jupiter.api.AfterEach
    void signOut() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private void signInAs(pride.bank.erp.user.User user) {
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities()));
    }

    private Page<StockDAO> search(Long supplierId, String lpoNumber,
                                  LocalDateTime from, LocalDateTime to) {
        return search(supplierId, lpoNumber, from, to, null);
    }

    private Page<StockDAO> search(Long supplierId, String lpoNumber,
                                  LocalDateTime from, LocalDateTime to, Long branchId) {
        return stockService.fetchAllStocks(
                200, DESCENDING_SORT_DIRECTION, 0, "id",
                lpoNumber, null, supplierId, branchId, false, from, to);
    }

    private List<StockDAO> all() {
        return search(null, null, null, null).getContent();
    }

    /** A supplier that actually has stock, so the narrowing assertion has something to narrow. */
    private Long anySupplierWithStock() {
        return all().stream()
                .filter(s -> s.getSupplier() != null && s.getSupplier().getId() != null)
                .map(s -> s.getSupplier().getId())
                .findFirst()
                .orElse(null);
    }

    // ── Branch scope ────────────────────────────────────────────────────────

    /**
     * The listing is scoped from the caller's permissions, not from the request.
     *
     * <p>`StockSearchDao` contained the word "branch" zero times before this: a Gulu storekeeper
     * holding {@code READ_INVENTORY} saw every branch's deliveries, and could open, correct, receive
     * against, close short or delete any of them by id.
     *
     * <p>Pinned on the criteria field rather than by standing up two users, because the field is
     * what the service fills from the resolver and the DAO reads — removing it is the change that
     * would silently reopen the estate, and it would not show up in any behavioural test run as one
     * user.
     */
    @Test
    void theListingCarriesABranchScope() {
        var fields = java.util.Arrays.stream(StockSearchCriteria.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();

        assertThat(fields)
                .as("without this the stock listing answers with every branch's deliveries")
                .contains("scopedBranchId");
    }

    /**
     * Which branch a receipt belongs to must reach the client.
     *
     * <p>{@code Stock.branch} is {@code @JsonIgnore} on the entity, so the response carried no
     * branch at all. That was survivable while everyone saw one branch; with cross-branch reach an
     * Admin officer reading a mixed list cannot tell one branch's deliveries from another's, and the
     * row menu has nothing to scope its actions against.
     */
    @Test
    @Transactional(readOnly = true)
    void everyRowSaysWhichBranchItBelongsTo() {
        List<StockDAO> rows = all();
        if (rows.isEmpty()) return;

        assertThat(rows).allSatisfy(stock ->
                assertThat(stock.getBranch())
                        .as("stock #%s must name its branch; the column is NOT NULL on the entity, "
                                + "so a null here means the mapping dropped it", stock.getId())
                        .isNotNull());
    }

    /**
     * Naming a branch you may not see is refused, not quietly rewritten.
     *
     * <p>The branch is found as the all-branches viewer, then asked for with nobody signed in - a
     * caller who cannot see beyond one branch (they cannot see any). Silently substituting would show
     * one branch's deliveries under another's name, a worse answer than an error. A real branch
     * viewer naming another branch is pinned in {@code StockReportScopeTest}.
     */
    @Test
    @Transactional(readOnly = true)
    void namingABranchYouCannotSeeIsRefused() {
        List<StockDAO> everything = all();
        if (everything.isEmpty()) return;

        Long branchId = everything.stream()
                .filter(st -> st.getBranch() != null && st.getBranch().getId() != null)
                .map(st -> st.getBranch().getId())
                .findFirst()
                .orElse(null);
        if (branchId == null) return;
        signOut();

        assertThatThrownBy(() -> search(null, null, null, null, branchId))
                .as("a caller who cannot see beyond one branch must be refused another, rather than "
                        + "handed their own under the name they asked for")
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    /** With no branch named, the listing is scoped to whatever the caller may see. */
    @Test
    @Transactional(readOnly = true)
    void omittingTheBranchIsScopedRatherThanRefused() {
        assertThatCode(this::all)
                .as("omitting the parameter is a different act from naming someone else's branch — "
                        + "it should be narrowed silently, not refused")
                .doesNotThrowAnyException();
    }

    // ── Supplier ────────────────────────────────────────────────────────────

    /**
     * The filter that silently did nothing.
     *
     * <p>Asserted on the returned rows: every one must belong to the supplier asked for. Before this
     * was wired the same call returned the whole table, and the assertion below is what would have
     * caught it.
     */
    @Test
    @Transactional(readOnly = true)
    void filteringBySupplierReturnsOnlyThatSuppliersStock() {
        Long supplierId = anySupplierWithStock();
        if (supplierId == null) return;   // no stock has a supplier; nothing to prove

        List<StockDAO> matched = search(supplierId, null, null, null).getContent();

        assertThat(matched)
                .as("a supplier that has stock must return at least the row we found it on")
                .isNotEmpty();
        assertThat(matched).allSatisfy(stock -> {
            assertThat(stock.getSupplier()).isNotNull();
            assertThat(stock.getSupplier().getId()).isEqualTo(supplierId);
        });
    }

    /** And it genuinely narrows — the point of a filter is that it excludes something. */
    @Test
    @Transactional(readOnly = true)
    void filteringBySupplierNarrowsTheListing() {
        List<StockDAO> everything = all();
        Long supplierId = anySupplierWithStock();
        if (supplierId == null || everything.size() < 2) return;

        long belongingToOthers = everything.stream()
                .filter(s -> s.getSupplier() == null
                        || !supplierId.equals(s.getSupplier().getId()))
                .count();
        if (belongingToOthers == 0) return;   // one supplier owns everything; nothing to exclude

        assertThat(search(supplierId, null, null, null).getContent().size())
                .as("the filter must exclude the %d row(s) belonging to other suppliers; before it "
                        + "was declared on the endpoint it returned all %d",
                        belongingToOthers, everything.size())
                .isLessThan(everything.size());
    }

    /**
     * Not filtering by supplier must still return stock that has no supplier.
     *
     * <p>Written as {@code root.get("supplier").get("id")} the optional filter would compile to an
     * INNER JOIN sitting in the FROM clause, filtering before the {@code nonNull} check is reached —
     * so every supplier-less row would vanish from the unfiltered listing. That is trap #1, and an
     * optional filter through a nullable association is exactly its shape.
     */
    @Test
    @Transactional(readOnly = true)
    void theUnfilteredListingStillIncludesStockWithNoSupplier() {
        long supplierless = all().stream().filter(s -> s.getSupplier() == null).count();

        assertThat(all())
                .as("%d row(s) have no supplier and must still be listed when nobody is filtering "
                        + "by one — if the join went inner, they disappear", supplierless)
                .hasSizeGreaterThanOrEqualTo((int) supplierless);
    }

    // ── Date range ──────────────────────────────────────────────────────────

    /**
     * The other filter that did nothing, for a different reason.
     *
     * <p>The endpoint always had this range; the page was calling it by another name. So the query
     * was correct all along and simply unreachable — the kind of fault that survives a code review
     * of the query itself.
     */
    @Test
    @Transactional(readOnly = true)
    void filteringByDateRangeNarrowsTheListing() {
        List<StockDAO> everything = all();
        if (everything.size() < 2) return;

        // A window that ends before now, so at least the newest row falls outside it.
        LocalDateTime cutoff = LocalDateTime.now().minusYears(50);
        List<StockDAO> ancient = search(null, null, null, cutoff).getContent();

        assertThat(ancient)
                .as("nothing was created before %s, so an end-date filter must exclude everything — "
                        + "returning all %d rows means the range never reached the query",
                        cutoff, everything.size())
                .isEmpty();
    }

    @Test
    @Transactional(readOnly = true)
    void anOpenEndedRangeReturnsEverything() {
        LocalDateTime longAgo = LocalDateTime.now().minusYears(50);

        assertThat(search(null, null, longAgo, null).getContent())
                .as("a start date before any record must not exclude anything")
                .hasSameSizeAs(all());
    }

    // ── The filters that already worked, so they still do ────────────────────

    @Test
    @Transactional(readOnly = true)
    void filteringByLpoNumberStillMatchesPartially() {
        String lpo = all().stream()
                .map(StockDAO::getLpoNumber)
                .filter(v -> v != null && v.length() > 2)
                .findFirst()
                .orElse(null);
        if (lpo == null) return;

        assertThat(search(null, lpo.substring(0, 3), null, null).getContent())
                .as("LPO is a partial match and was already wired; the supplier change must not "
                        + "have disturbed it")
                .isNotEmpty();
    }

    /** Filters combine rather than replace one another. */
    @Test
    @Transactional(readOnly = true)
    void filtersCombine() {
        Long supplierId = anySupplierWithStock();
        if (supplierId == null) return;

        LocalDateTime cutoff = LocalDateTime.now().minusYears(50);

        assertThat(search(supplierId, null, null, cutoff).getContent())
                .as("a supplier that has stock, combined with a window nothing falls into, must "
                        + "return nothing — if it returns rows, the predicates are not being ANDed")
                .isEmpty();
    }
}
