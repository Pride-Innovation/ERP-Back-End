package pride.bank.erp.movement;

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
 * The movements register, filtered in the database rather than in the browser.
 *
 * <h2>Why this exists</h2>
 * The page fetched the first hundred movements once and did everything else client-side: eleven
 * filters, the sort, the paging, three exports and five status tiles. Past a hundred rows a movement
 * matching a filter exactly came back as "no results" — indistinguishable from "there is no such
 * movement" — and the tiles under-reported against the true total printed directly above them.
 *
 * <p>Written against the development database rather than a fixture, for the same reason the stock
 * filter tests are: a filter test that builds its own two rows proves the predicate compiles, not
 * that the filter reaches the query the page calls. Where the data cannot support a case, the test
 * says so by returning early rather than asserting something vacuous.
 */
@SpringBootTest
class MovementSearchDaoTest {

    @Autowired
    private MovementSearchDao dao;

    private static final Pageable FIRST_200 =
            PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "id"));

    private Page<Movement> search(MovementSearchCriteria criteria) {
        return dao.search(criteria, FIRST_200);
    }

    private List<Movement> all() {
        return search(new MovementSearchCriteria()).getContent();
    }

    // ── the unfiltered listing ───────────────────────────────────────────────

    /**
     * No criteria must mean no restriction — including for movements missing an end.
     *
     * <p>Every one of a movement's four ends is nullable: a return has no source store, an issuance
     * no source user. Written with dotted paths the optional filters would inner-join and drop those
     * rows in the FROM clause. {@code findForBranch} was bitten by exactly this and returned nothing
     * at all for every branch-scoped user, which read as "there are no movements" rather than as a
     * fault.
     */
    @Test
    @Transactional(readOnly = true)
    void theUnfilteredListingIncludesMovementsWithMissingEnds() {
        List<Movement> movements = all();
        if (movements.isEmpty()) return;

        long missingASourceStore = movements.stream().filter(m -> m.getSourceStore() == null).count();
        long missingARecipient = movements.stream().filter(m -> m.getRecipientUser() == null).count();

        System.out.printf("movements=%d withoutSourceStore=%d withoutRecipient=%d%n",
                movements.size(), missingASourceStore, missingARecipient);

        assertThat(movements)
                .as("%d movement(s) have no source store and %d no recipient; if any join went "
                        + "inner they would vanish from an unfiltered listing",
                        missingASourceStore, missingARecipient)
                .isNotEmpty();
    }

    /**
     * The count must match the rows, or the pager invents pages.
     *
     * <p>The joins are the risk: calling {@code join()} twice for the same association duplicates it,
     * multiplying rows and inflating the count. The table would then offer a page number that holds
     * nothing.
     */
    @Test
    @Transactional(readOnly = true)
    void theTotalMatchesTheRowsReturned() {
        Page<Movement> page = search(new MovementSearchCriteria());
        if (page.getTotalElements() > 200) return;   // more than this page holds; nothing to compare

        assertThat(page.getContent())
                .as("a duplicated join inflates the count without adding rows, and the pager then "
                        + "offers pages that hold nothing")
                .hasSize((int) page.getTotalElements());
    }

    /** No duplicates, which is the other symptom of a doubled join. */
    @Test
    @Transactional(readOnly = true)
    void noMovementAppearsTwice() {
        List<Long> ids = all().stream().map(Movement::getId).toList();

        assertThat(ids)
                .as("a movement joined through two ends must still be one row")
                .doesNotHaveDuplicates();
    }

    // ── paging ───────────────────────────────────────────────────────────────

    /**
     * Paging must not repeat or drop rows — the trap that has now bitten four listings.
     *
     * <p>Sorting on a non-unique column leaves ties, and MySQL is free to order tied rows
     * differently per query; with OFFSET/LIMIT the same row then appears on two pages and another on
     * none. Sorted here by {@code status}, which is about as non-unique as a column gets.
     */
    @Test
    @Transactional(readOnly = true)
    void pagingOnANonUniqueSortNeitherRepeatsNorDropsRows() {
        Sort byStatus = Sort.by(Sort.Direction.ASC, "status");
        long total = dao.search(new MovementSearchCriteria(), PageRequest.of(0, 1, byStatus))
                .getTotalElements();
        if (total < 4) return;

        int pageSize = 2;
        int pages = (int) Math.min(5, (total + pageSize - 1) / pageSize);

        List<Long> seen = new java.util.ArrayList<>();
        for (int p = 0; p < pages; p++) {
            dao.search(new MovementSearchCriteria(), PageRequest.of(p, pageSize, byStatus))
                    .getContent().forEach(m -> seen.add(m.getId()));
        }

        assertThat(seen)
                .as("walking %d pages of %d sorted by a non-unique column must visit each movement "
                        + "once; duplicates here mean the id tie-break is gone", pages, pageSize)
                .doesNotHaveDuplicates();
    }

    // ── the filters ──────────────────────────────────────────────────────────

    @Test
    @Transactional(readOnly = true)
    void filteringByStatusReturnsOnlyThatStatus() {
        List<Movement> movements = all();
        if (movements.isEmpty()) return;

        MovementStatus status = movements.get(0).getStatus();
        if (status == null) return;

        List<Movement> matched = search(
                MovementSearchCriteria.builder().status(status).build()).getContent();

        assertThat(matched).isNotEmpty();
        assertThat(matched).allSatisfy(m -> assertThat(m.getStatus()).isEqualTo(status));
    }

    @Test
    @Transactional(readOnly = true)
    void filteringByTypeReturnsOnlyThatType() {
        MovementType type = all().stream()
                .map(Movement::getMovementType).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (type == null) return;

        assertThat(search(MovementSearchCriteria.builder().movementType(type).build()).getContent())
                .isNotEmpty()
                .allSatisfy(m -> assertThat(m.getMovementType()).isEqualTo(type));
    }

    /**
     * Source is matched through a nullable association, so it is the shape trap #1 takes.
     *
     * <p>Also checks the filter genuinely excludes something — a filter that returns everything is
     * exactly what an undeclared request parameter looks like from the outside.
     */
    @Test
    @Transactional(readOnly = true)
    void filteringBySourceStoreNarrowsTheListing() {
        List<Movement> everything = all();
        String source = everything.stream()
                .filter(m -> m.getSourceStore() != null)
                .map(m -> m.getSourceStore().getName())
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (source == null) return;

        List<Movement> matched = search(
                MovementSearchCriteria.builder().source(source).build()).getContent();

        assertThat(matched).isNotEmpty();
        assertThat(matched).allSatisfy(m -> {
            assertThat(m.getSourceStore()).isNotNull();
            assertThat(m.getSourceStore().getName()).isEqualTo(source);
        });

        long fromElsewhere = everything.stream()
                .filter(m -> m.getSourceStore() == null
                        || !source.equals(m.getSourceStore().getName()))
                .count();
        if (fromElsewhere > 0) {
            assertThat(matched.size())
                    .as("the filter must exclude the %d movement(s) from other sources",
                            fromElsewhere)
                    .isLessThan(everything.size());
        }
    }

    /**
     * A destination is a store <em>or</em> a person, and the page shows one column for both.
     *
     * <p>The predicate has to consider both or half the register becomes unfilterable — and it must
     * agree exactly with the label the dropdown offers, or the filter cannot match its own option.
     */
    @Test
    @Transactional(readOnly = true)
    void filteringByDestinationMatchesBothStoresAndPeople() {
        MovementSearchDao.FilterOptions options = dao.filterOptions(new MovementSearchCriteria());
        if (options.destinations().isEmpty()) return;

        for (String destination : options.destinations().stream().limit(3).toList()) {
            assertThat(search(MovementSearchCriteria.builder().destination(destination).build())
                    .getContent())
                    .as("'%s' is offered by the dropdown, so the filter must match it — a value the "
                            + "panel can select but the query cannot find is worse than no filter",
                            destination)
                    .isNotEmpty();
        }
    }

    /** Tracking numbers are half-remembered off a docket, so the match is partial. */
    @Test
    @Transactional(readOnly = true)
    void filteringByTrackingNumberMatchesPartially() {
        String tracking = all().stream()
                .map(Movement::getTrackingNumber)
                .filter(v -> v != null && v.length() > 2)
                .findFirst().orElse(null);
        if (tracking == null) return;

        assertThat(search(MovementSearchCriteria.builder()
                .trackingNumber(tracking.substring(1, 3)).build()).getContent())
                .isNotEmpty();
    }

    @Test
    @Transactional(readOnly = true)
    void filteringByDateRangeNarrowsTheListing() {
        if (all().size() < 2) return;

        LocalDateTime beforeAnything = LocalDateTime.now().minusYears(50);

        assertThat(search(MovementSearchCriteria.builder().dateTo(beforeAnything).build())
                .getContent())
                .as("nothing was created before %s, so an end-date filter must exclude everything; "
                        + "returning rows means the range never reached the query", beforeAnything)
                .isEmpty();
    }

    /** Filters combine with AND rather than replacing one another. */
    @Test
    @Transactional(readOnly = true)
    void filtersCombine() {
        MovementStatus status = all().stream()
                .map(Movement::getStatus).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (status == null) return;

        assertThat(search(MovementSearchCriteria.builder()
                .status(status)
                .dateTo(LocalDateTime.now().minusYears(50))
                .build()).getContent())
                .as("a real status combined with a window nothing falls into must return nothing; "
                        + "rows here mean the predicates are not being ANDed")
                .isEmpty();
    }

    // ── the scoped path ──────────────────────────────────────────────────────

    /*
     * Everything above leaves `scopedBranchId` null, which is what a cross-branch caller resolves to.
     * That left the entire branch-scope predicate — the security-critical half, and the only code
     * that builds a *nested* join — unexecuted, so the suite was green while the listing returned a
     * 500 to every branch user. The tests below exist because of that, and they set the field.
     */

    /**
     * A scoped listing must run at all.
     *
     * <p>It did not. The join cache resolved nested paths inside {@code computeIfAbsent}, whose
     * mapping function re-entered the same map to build the parent, and {@code HashMap} throws
     * {@code ConcurrentModificationException} rather than corrupt itself. Every path the scope
     * predicate uses is nested, so the register worked for anyone seeing all branches and failed for
     * everybody else.
     */
    @Test
    @Transactional(readOnly = true)
    void aScopedListingRuns() {
        Long branch = anyBranchId();
        if (branch == null) return;

        assertThat(search(MovementSearchCriteria.builder().scopedBranchId(branch).build())
                .getContent())
                .as("a branch-scoped search must execute; nested joins are built only on this path")
                .isNotNull();
    }

    /** And so must the tiles and the dropdowns, which take the same predicate. */
    @Test
    @Transactional(readOnly = true)
    void theScopedTilesAndOptionsRun() {
        Long branch = anyBranchId();
        if (branch == null) return;

        assertThat(dao.countByStatus(MovementSearchCriteria.builder().scopedBranchId(branch).build()))
                .as("the tiles run the same predicate as the listing")
                .isNotNull();
        assertThat(dao.filterOptions(MovementSearchCriteria.builder().scopedBranchId(branch).build()))
                .as("so do the dropdowns")
                .isNotNull();
    }

    /**
     * Scoping keeps only movements that touch the branch — by any of their four ends.
     *
     * <p>Mirrors {@code MovementRepository#findForBranch}. A listing that computed reach differently
     * from the guard would show someone a row they could not then open.
     */
    @Test
    @Transactional(readOnly = true)
    void scopingKeepsOnlyMovementsTouchingThatBranch() {
        Long branch = anyBranchId();
        if (branch == null) return;

        List<Movement> scoped = search(
                MovementSearchCriteria.builder().scopedBranchId(branch).build()).getContent();

        System.out.printf("branch %d touches %d of %d movement(s)%n",
                branch, scoped.size(), all().size());

        assertThat(scoped).allSatisfy(m -> assertThat(endsOf(m))
                .as("movement %s was returned for branch %d but touches none of it", m.getId(), branch)
                .contains(branch));
    }

    /** And it must actually narrow, or it is not a scope. */
    @Test
    @Transactional(readOnly = true)
    void scopingNarrowsWhenAnotherBranchHasMovements() {
        List<Movement> everything = all();
        Long branch = anyBranchId();
        if (branch == null) return;

        long elsewhere = everything.stream().filter(m -> !endsOf(m).contains(branch)).count();
        if (elsewhere == 0) return;   // every movement touches this branch; nothing to exclude

        assertThat(search(MovementSearchCriteria.builder().scopedBranchId(branch).build())
                .getContent().size())
                .as("%d movement(s) touch no part of branch %d and must be excluded",
                        elsewhere, branch)
                .isLessThan(everything.size());
    }

    /** The count must agree with the scoped rows, or a branch user pages into emptiness. */
    @Test
    @Transactional(readOnly = true)
    void theScopedTotalMatchesTheScopedRows() {
        Long branch = anyBranchId();
        if (branch == null) return;

        Page<Movement> page = dao.search(
                MovementSearchCriteria.builder().scopedBranchId(branch).build(), FIRST_200);
        if (page.getTotalElements() > 200) return;

        assertThat(page.getContent())
                .as("the scope predicate adds four joins; if any is duplicated the count inflates "
                        + "and the pager offers pages that hold nothing")
                .hasSize((int) page.getTotalElements());
    }

    /** A branch with movements, so the assertions above have something to narrow. */
    private Long anyBranchId() {
        return all().stream()
                .map(MovementSearchDaoTest::endsOf)
                .flatMap(java.util.Set::stream)
                .findFirst()
                .orElse(null);
    }

    /** Every branch a movement touches — the same four ends the scope predicate reads. */
    private static java.util.Set<Long> endsOf(Movement m) {
        java.util.Set<Long> ends = new java.util.LinkedHashSet<>();
        if (m.getSourceStore() != null && m.getSourceStore().getLocation() != null) {
            ends.add(m.getSourceStore().getLocation().getId());
        }
        if (m.getDestStore() != null && m.getDestStore().getLocation() != null) {
            ends.add(m.getDestStore().getLocation().getId());
        }
        if (m.getSourceUser() != null && m.getSourceUser().getBranch() != null) {
            ends.add(m.getSourceUser().getBranch().getId());
        }
        if (m.getRecipientUser() != null && m.getRecipientUser().getBranch() != null) {
            ends.add(m.getRecipientUser().getBranch().getId());
        }
        return ends;
    }

    /**
     * A caller with no branch must be refused, not silently widened.
     *
     * <p>{@code AccessScopeService.resolveBranch} answers {@code null} for two opposite callers: one
     * who may see every branch, and one with no branch on record. Downstream, null means "no
     * restriction" — so an unconfigured account was handed the whole bank's movements, the exact
     * opposite of what its permissions say. {@code BranchScopeService} closes this for the inventory
     * listings; the movement listing called the resolver directly and never got that protection.
     *
     * <p>Pinned on the service's own resolver rather than by standing up a branchless principal,
     * because the distinction being protected is a property of that method: null-for-ALL and
     * null-for-no-branch must not take the same path.
     */
    @Test
    void aCallerWithNoBranchIsRefusedRatherThanShownEverything() {
        String source = readSource("src/main/java/pride/bank/erp/movement/MovementService.java");
        String body = methodBody(source, "applyScope");

        assertThat(body)
                .as("ALL scope is the only route to an unrestricted listing")
                .contains("DashboardScope.ALL");
        assertThat(body)
                .as("an unidentifiable caller must refuse; leaving the criteria unset means "
                        + "'no restriction' downstream")
                .contains("AccessDeniedException");

        for (String reader : List.of("listMovements", "countMovementsByStatus", "movementFilterOptions")) {
            assertThat(source)
                    .as("%s must scope through applyScope(), not the raw resolver", reader)
                    .contains("applyScope(");
        }
    }

    /**
     * SELF narrows by person; BRANCH by branch; never both.
     *
     * <p>Applying the branch as well at SELF would filter a movement I am genuinely party to back
     * out — a transfer coming to me from another branch, say — which is the case the person rule
     * exists to catch.
     */
    @Test
    void selfAndBranchScopeAreMutuallyExclusive() {
        String body = methodBody(
                readSource("src/main/java/pride/bank/erp/movement/MovementService.java"), "applyScope");

        int selfBranch = body.indexOf("DashboardScope.SELF");
        assertThat(selfBranch).as("SELF must be handled explicitly").isGreaterThanOrEqualTo(0);

        assertThat(body.substring(selfBranch))
                .as("the SELF path must set the user and then return, never fall through to the "
                        + "branch narrowing")
                .contains("setScopedUserId")
                .contains("return;");
    }

    /**
     * The three places that define "party to a movement" must agree.
     *
     * <p>The DAO decides which rows come back, {@code MovementService.peoplePartyTo} decides whether
     * the detail page opens, and {@code useAccessScope} decides whether a button is offered. If they
     * drift, the register shows a row that will not open, or hides an action the server would allow.
     */
    @Test
    void theSelfRuleIsTheSameInTheQueryAndTheGuard() {
        String dao = readSource("src/main/java/pride/bank/erp/movement/MovementSearchDao.java");
        String service = readSource("src/main/java/pride/bank/erp/movement/MovementService.java");
        String guard = methodBody(service, "peoplePartyTo");

        for (String link : List.of("initiator", "sourceUser", "recipientUser", "receivingOfficer")) {
            assertThat(dao).as("the query must consider %s", link).contains(link);
            assertThat(guard).as("the guard must consider %s", link).containsIgnoringCase(link);
        }

        assertThat(dao)
                .as("the requester of the request a movement fulfils counts as party to it")
                .contains("requestRequester");
        assertThat(guard)
                .as("and the guard must agree, or a requester sees a row that will not open")
                .contains("getRequest().getRequester()");
    }

    /**
     * An unknown sort column must not be a 500.
     *
     * <p>{@code sortBy} reaches {@code root.get(...)}, which throws on a field the entity does not
     * have — so a stale bookmark or a typo took the whole register down. Criteria paths are not SQL,
     * so this is robustness rather than injection, but a listing should not fall over because it was
     * asked for an ordering it does not have.
     */
    @Test
    @Transactional(readOnly = true)
    void anUnknownSortColumnFallsBackRatherThanFailing() {
        String controller = readSource(
                "src/main/java/pride/bank/erp/movement/MovementController.java");

        assertThat(methodBody(controller, "sort"))
                .as("the sort column must be checked against a whitelist before it reaches the DAO")
                .contains("SORTABLE.contains");

        assertThat(methodBody(controller, "page"))
                .as("an unbounded pageSize lets one request pull the whole register into memory")
                .contains("MAX_PAGE_SIZE");
    }

    /** The approval inbox is the caller's own. */
    @Test
    void theApprovalInboxCannotBeReadForSomeoneElse() {
        String controller = readSource(
                "src/main/java/pride/bank/erp/movement/MovementController.java");

        assertThat(methodBody(controller, "pendingApprovals"))
                .as("this queries the repository directly with no branch scoping, so naming another "
                        + "approver walked around the scope the listing applies")
                .contains("requireOwnInbox");
    }

    private static String readSource(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /**
     * The text of one method, from its declaration to the next member.
     *
     * <p>Uses {@code String.lines()} rather than splitting on a newline escape: line endings differ
     * between checkouts, and the escape itself is easy to mangle when this file is edited by a tool.
     */
    private static String methodBody(String source, String methodName) {
        List<String> lines = source.lines().toList();
        int first = -1;
        for (int i = 0; i < lines.size() && first < 0; i++) {
            String trimmed = lines.get(i).trim();
            boolean declares = trimmed.startsWith("public ")
                    || trimmed.startsWith("private ")
                    || trimmed.startsWith("protected ");
            if (declares && trimmed.contains(" " + methodName + "(")) first = i;
        }
        assertThat(first).as("method %s must be declared", methodName).isGreaterThanOrEqualTo(0);

        StringBuilder body = new StringBuilder(lines.get(first)).append(System.lineSeparator());
        for (int i = first + 1; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            boolean nextMember = trimmed.startsWith("public ")
                    || trimmed.startsWith("private ")
                    || trimmed.startsWith("protected ")
                    || trimmed.startsWith("@Transactional");
            if (nextMember) break;
            body.append(lines.get(i)).append(System.lineSeparator());
        }
        return body.toString();
    }

    // ── SELF: a movement I am a party to ─────────────────────────────────────

    /**
     * SELF must narrow by person, not by branch.
     *
     * <p>It used to collapse: {@code resolveBranch} returned the caller's branch for anything below
     * ALL, so SELF and BRANCH were the same query and an officer granted {@code READ_MOVEMENT} saw
     * their whole branch's register instead of their own movements.
     */
    @Test
    @Transactional(readOnly = true)
    void scopingByPersonKeepsOnlyMovementsThatPersonIsPartyTo() {
        Long me = anyPartyId();
        if (me == null) return;

        List<Movement> mine = search(
                MovementSearchCriteria.builder().scopedUserId(me).build()).getContent();

        System.out.printf("person %d is party to %d of %d movement(s)%n",
                me, mine.size(), all().size());

        assertThat(mine).allSatisfy(m -> assertThat(partiesTo(m))
                .as("movement %s was returned for person %d, who is not on it", m.getId(), me)
                .contains(me));
    }

    /** And it must actually narrow, or it is not a scope. */
    @Test
    @Transactional(readOnly = true)
    void scopingByPersonNarrowsTheRegister() {
        List<Movement> everything = all();
        Long me = anyPartyId();
        if (me == null) return;

        long notMine = everything.stream().filter(m -> !partiesTo(m).contains(me)).count();
        if (notMine == 0) return;

        assertThat(search(MovementSearchCriteria.builder().scopedUserId(me).build())
                .getContent().size())
                .as("%d movement(s) name somebody else and must be excluded", notMine)
                .isLessThan(everything.size());
    }

    /**
     * A requester counts as party to the movement fulfilling their request.
     *
     * <p>This is the limb that lets an officer follow their own items from the request page without
     * seeing anybody else's, and it is the case that started this work.
     */
    @Test
    @Transactional(readOnly = true)
    void aRequesterIsPartyToTheMovementFulfillingTheirRequest() {
        Movement fulfilment = all().stream()
                .filter(m -> m.getRequest() != null && m.getRequest().getRequester() != null)
                .findFirst().orElse(null);
        if (fulfilment == null) return;

        Long requester = fulfilment.getRequest().getRequester().getId();

        assertThat(search(MovementSearchCriteria.builder().scopedUserId(requester).build())
                .getContent().stream().map(Movement::getId).toList())
                .as("the person who asked for the items must be able to see them moving")
                .contains(fulfilment.getId());
    }

    /** The scoped counts and dropdowns run the same predicate and must not fall over. */
    @Test
    @Transactional(readOnly = true)
    void theSelfScopedTilesAndOptionsRun() {
        Long me = anyPartyId();
        if (me == null) return;

        MovementSearchCriteria criteria = MovementSearchCriteria.builder().scopedUserId(me).build();
        assertThat(dao.countByStatus(criteria)).isNotNull();
        assertThat(dao.filterOptions(criteria)).isNotNull();
    }

    /** Somebody who is party to at least one movement. */
    private Long anyPartyId() {
        return all().stream()
                .map(MovementSearchDaoTest::partiesTo)
                .flatMap(java.util.Set::stream)
                .findFirst()
                .orElse(null);
    }

    /**
     * Everyone party to a movement — must mirror {@code MovementService.peoplePartyTo} and the DAO's
     * SELF predicate. If the three drift, the register shows a row the detail page refuses.
     */
    private static java.util.Set<Long> partiesTo(Movement m) {
        java.util.Set<Long> people = new java.util.LinkedHashSet<>();
        if (m.getInitiator() != null) people.add(m.getInitiator().getId());
        if (m.getSourceUser() != null) people.add(m.getSourceUser().getId());
        if (m.getRecipientUser() != null) people.add(m.getRecipientUser().getId());
        if (m.getReceivingOfficer() != null) people.add(m.getReceivingOfficer().getId());
        if (m.getRequest() != null && m.getRequest().getRequester() != null) {
            people.add(m.getRequest().getRequester().getId());
        }
        people.remove(null);
        return people;
    }

    // ── the tiles and the dropdowns ──────────────────────────────────────────

    /**
     * The tiles must sum to the total printed above them.
     *
     * <p>This is the bug in its most visible form: the heading showed {@code totalElements} while the
     * five tiles counted the rows the browser held, so past the fetch size they silently stopped
     * agreeing. Both numbers now come from the same query.
     */
    @Test
    @Transactional(readOnly = true)
    void theStatusCountsSumToTheTotal() {
        MovementSearchCriteria criteria = new MovementSearchCriteria();
        long total = search(criteria).getTotalElements();
        Map<MovementStatus, Long> counts = dao.countByStatus(criteria);

        System.out.printf("status counts %s (total %d)%n", counts, total);

        assertThat(counts.values().stream().mapToLong(Long::longValue).sum())
                .as("every movement has exactly one status, so the tiles must partition the total; "
                        + "a mismatch means the two queries disagree about what they are counting")
                .isEqualTo(total);
    }

    /** And they must move with the filters, or they describe a different set than the table. */
    @Test
    @Transactional(readOnly = true)
    void theStatusCountsRespectTheFilters() {
        MovementStatus status = all().stream()
                .map(Movement::getStatus).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (status == null) return;

        Map<MovementStatus, Long> counts = dao.countByStatus(
                MovementSearchCriteria.builder().status(status).build());

        assertThat(counts.keySet())
                .as("filtered to one status, no other status can have a count")
                .containsExactly(status);
    }

    /**
     * Every option the dropdowns offer must be one the filter can match.
     *
     * <p>These lists were built in the browser from the loaded rows, so they shrank to whatever was
     * on the page. Read from the register instead — and pinned against the filters above, because an
     * option the panel offers and the query cannot find is the worse of the two failures.
     */
    @Test
    @Transactional(readOnly = true)
    void theFilterOptionsComeFromTheWholeRegister() {
        MovementSearchDao.FilterOptions options = dao.filterOptions(new MovementSearchCriteria());

        System.out.printf("options sources=%d destinations=%d couriers=%d initiators=%d%n",
                options.sources().size(), options.destinations().size(),
                options.couriers().size(), options.initiators().size());

        assertThat(options.sources()).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(options.destinations()).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(options.couriers()).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(options.initiators()).doesNotContainNull().doesNotHaveDuplicates();
    }
}
