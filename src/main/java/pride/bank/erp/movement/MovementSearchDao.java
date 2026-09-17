package pride.bank.erp.movement;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Filtering, sorting and paging for the movements register — in the database, not the browser.
 *
 * <h2>What this replaces</h2>
 * The page fetched the first hundred movements once and did everything else client-side: eleven
 * filters, the sort, the pagination, the exports and the five status tiles. Past a hundred rows the
 * register simply stopped: a movement matching a filter exactly came back as "no results", which
 * reads the same as "there is no such movement", and the tiles under-reported without saying so.
 *
 * <h2>Three traps this file is written against</h2>
 * <ul>
 *   <li><b>Every join is an explicit LEFT JOIN, and each is created once.</b> A movement's ends are
 *       all nullable — a return has no source store, an issuance no source user — so a dotted path
 *       like {@code m.sourceStore.name} would inner-join and drop rows in the FROM clause before any
 *       predicate ran. {@code findForBranch} was bitten by exactly this and returned nothing at all
 *       for every branch-scoped user. Joins are also cached, because calling {@code join()} twice
 *       for the same association duplicates it and inflates both the rows and the count.</li>
 *   <li><b>The sort always ends in {@code id}.</b> Ordering on a non-unique column leaves ties that
 *       MySQL may order differently per query, so with OFFSET/LIMIT the same row appears on two
 *       pages and another on none.</li>
 *   <li><b>The count query applies the same predicate</b> and builds its own joins. Reusing the
 *       select query's root would count through its fetches.</li>
 * </ul>
 */
@Repository
public class MovementSearchDao {

    private final EntityManager entityManager;
    private final CriteriaBuilder cb;

    public MovementSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.cb = entityManager.getCriteriaBuilder();
    }

    /** One page of movements matching the criteria, newest first unless told otherwise. */
    public Page<Movement> search(MovementSearchCriteria criteria, Pageable pageable) {
        CriteriaQuery<Movement> query = cb.createQuery(Movement.class);
        Root<Movement> root = query.from(Movement.class);
        Joins joins = new Joins(root);

        query.where(predicate(criteria, root, joins));
        query.distinct(true);
        order(query, root, pageable);

        TypedQuery<Movement> typed = entityManager.createQuery(query);
        typed.setFirstResult((int) pageable.getOffset());
        typed.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(typed.getResultList(), pageable, count(criteria));
    }

    /**
     * How many movements sit in each status, across everything the criteria match.
     *
     * <p>The page's five tiles counted the rows it had loaded while the heading beside them showed
     * the true total, so past a hundred movements the tiles quietly stopped summing to the number
     * printed above them. Counted here instead, in one grouped query, so both numbers come from the
     * same place and the filters move them together.
     *
     * @return status → count, omitting statuses with no rows
     */
    public Map<MovementStatus, Long> countByStatus(MovementSearchCriteria criteria) {
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Movement> root = query.from(Movement.class);
        Joins joins = new Joins(root);

        Path<MovementStatus> status = root.get("status");
        query.where(predicate(criteria, root, joins));
        // countDistinct, because the destination and courier predicates can join to several rows.
        query.multiselect(status, cb.countDistinct(root.get("id")));
        query.groupBy(status);

        Map<MovementStatus, Long> counts = new LinkedHashMap<>();
        for (Object[] row : entityManager.createQuery(query).getResultList()) {
            counts.put((MovementStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * The distinct values behind the panel's four dropdowns.
     *
     * <p>These were derived in the browser from the loaded rows, so the options shrank to whatever
     * happened to be on the page — a filter that cannot offer a value it would have matched. Read
     * from the scoped register instead, so the list is what the caller could actually filter to.
     *
     * <p>Only the scope is applied, deliberately: the options must not narrow as filters are chosen,
     * or picking a source would empty the destination list and the panel could never be widened
     * again.
     */
    public FilterOptions filterOptions(MovementSearchCriteria scopeOnly) {
        return new FilterOptions(
                distinct(scopeOnly, (root, joins) -> joins.sourceStore().get("name")),
                distinct(scopeOnly, (root, joins) -> destinationLabel(joins)),
                distinct(scopeOnly, (root, joins) -> courierLabel(root, joins)),
                distinct(scopeOnly, (root, joins) -> initiatorLabel(joins)));
    }

    /** The option lists behind the source / destination / courier / initiator dropdowns. */
    public record FilterOptions(List<String> sources, List<String> destinations,
                                List<String> couriers, List<String> initiators) {
    }

    // ── predicate ───────────────────────────────────────────────────────────

    private Predicate predicate(MovementSearchCriteria c, Root<Movement> root, Joins joins) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(c.getDateFrom())) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createDate"), c.getDateFrom()));
        }
        if (Objects.nonNull(c.getDateTo())) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createDate"), c.getDateTo()));
        }
        if (Objects.nonNull(c.getMovementType())) {
            predicates.add(cb.equal(root.get("movementType"), c.getMovementType()));
        }
        if (Objects.nonNull(c.getMovementCategory())) {
            predicates.add(cb.equal(root.get("movementCategory"), c.getMovementCategory()));
        }
        if (Objects.nonNull(c.getStatus())) {
            predicates.add(cb.equal(root.get("status"), c.getStatus()));
        }
        if (Objects.nonNull(c.getReceiptStatus())) {
            predicates.add(cb.equal(root.get("receiptStatus"), c.getReceiptStatus()));
        }
        if (hasText(c.getTrackingNumber())) {
            // Partial and case-insensitive: the panel offers this as a free-text box, and a tracking
            // number is something people half-remember off a docket.
            predicates.add(cb.like(cb.lower(root.get("trackingNumber")),
                    "%" + c.getTrackingNumber().toLowerCase() + "%"));
        }
        if (hasText(c.getSource())) {
            predicates.add(cb.equal(joins.sourceStore().get("name"), c.getSource()));
        }
        if (hasText(c.getDestination())) {
            predicates.add(cb.equal(destinationLabel(joins), c.getDestination()));
        }
        if (hasText(c.getCourier())) {
            predicates.add(cb.equal(courierLabel(root, joins), c.getCourier()));
        }
        if (hasText(c.getInitiator())) {
            predicates.add(cb.equal(initiatorLabel(joins), c.getInitiator()));
        }

        /*
         * Scope: any one of the movement's four ends being the caller's branch puts it in reach.
         *
         * Mirrors MovementRepository#findForBranch deliberately. A guard or a listing that computed
         * this differently would show someone a row they could not then open, which is worse than
         * either rule on its own.
         */
        if (Objects.nonNull(c.getScopedBranchId())) {
            Long branch = c.getScopedBranchId();
            predicates.add(cb.or(
                    cb.equal(joins.sourceStoreLocation().get("id"), branch),
                    cb.equal(joins.destStoreLocation().get("id"), branch),
                    cb.equal(joins.sourceUserBranch().get("id"), branch),
                    cb.equal(joins.recipientUserBranch().get("id"), branch)));
        }

        /*
         * SELF: a movement I am a party to.
         *
         * A movement has no owner column, so "mine" is the union of the ways a person can be on one —
         * I raised it, it left my hands, it is coming to me, I signed for it, or it fulfils a request
         * I raised. That last limb is what lets a requester follow their own items without seeing
         * anybody else's, and it is the reason the request detail page can show a fulfilment movement
         * to the officer who asked for it.
         *
         * Every join is LEFT and cached: all five links are nullable, and an issuance has no source
         * user while a return has no recipient, so a dotted path would drop those rows in the FROM
         * clause before this OR was ever evaluated.
         */
        if (Objects.nonNull(c.getScopedUserId())) {
            Long me = c.getScopedUserId();
            predicates.add(cb.or(
                    cb.equal(joins.initiator().get("id"), me),
                    cb.equal(joins.sourceUser().get("id"), me),
                    cb.equal(joins.recipientUser().get("id"), me),
                    cb.equal(joins.receivingOfficer().get("id"), me),
                    cb.equal(joins.requestRequester().get("id"), me)));
        }

        return predicates.isEmpty()
                ? cb.conjunction()
                : cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Where the movement is going, as the one label the page shows.
     *
     * <p>A destination is a store or a person, never both, so this is the store's name falling back
     * to the recipient's full name — the same rule the front end's {@code movementDestLabel} applies,
     * kept identical on purpose so a value the dropdown offers is a value the filter can match.
     */
    private Expression<String> destinationLabel(Joins joins) {
        return cb.coalesce(
                joins.destStore().get("name"),
                cb.concat(cb.concat(joins.recipientUser().get("firstName"), " "),
                        joins.recipientUser().get("lastName")));
    }

    /** The courier record's name, falling back to the free-text service field. */
    private Expression<String> courierLabel(Root<Movement> root, Joins joins) {
        return cb.coalesce(joins.courier().get("name"), root.get("courierService"));
    }

    /** Whoever raised it, as "First Last". */
    private Expression<String> initiatorLabel(Joins joins) {
        return cb.concat(cb.concat(joins.initiator().get("firstName"), " "),
                joins.initiator().get("lastName"));
    }

    // ── count, ordering, distinct ────────────────────────────────────────────

    /** Its own root and joins — counting through the select query's would count its fetches. */
    private long count(MovementSearchCriteria criteria) {
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Movement> root = query.from(Movement.class);
        Joins joins = new Joins(root);

        query.select(cb.countDistinct(root.get("id")));
        query.where(predicate(criteria, root, joins));

        Long total = entityManager.createQuery(query).getSingleResult();
        return total != null ? total : 0L;
    }

    /**
     * Sort as asked, then always by id.
     *
     * <p>Movements are ordered by date, status or type from the table headers, and none of those is
     * unique. Without the tie-break, paging over tied rows repeats some and skips others; the table
     * looks like it is losing records rather than like it is sorted badly.
     */
    private void order(CriteriaQuery<Movement> query, Root<Movement> root, Pageable pageable) {
        List<Order> orders = new ArrayList<>();

        for (Sort.Order sort : pageable.getSort()) {
            if ("id".equals(sort.getProperty())) continue;   // added below regardless
            orders.add(sort.isAscending()
                    ? cb.asc(root.get(sort.getProperty()))
                    : cb.desc(root.get(sort.getProperty())));
        }

        boolean ascendingTail = pageable.getSort().stream()
                .filter(o -> !"id".equals(o.getProperty()))
                .reduce((first, second) -> second)
                .map(Sort.Order::isAscending)
                .orElse(false);   // no sort given → newest first, which is what the page wants

        orders.add(ascendingTail ? cb.asc(root.get("id")) : cb.desc(root.get("id")));
        query.orderBy(orders);
    }

    /** Distinct non-blank values of one expression, ordered, for a dropdown. */
    private List<String> distinct(MovementSearchCriteria criteria,
                                  java.util.function.BiFunction<Root<Movement>, Joins, Expression<String>> of) {
        CriteriaQuery<String> query = cb.createQuery(String.class);
        Root<Movement> root = query.from(Movement.class);
        Joins joins = new Joins(root);

        Expression<String> value = of.apply(root, joins);
        query.select(value).distinct(true);

        Predicate scoped = predicate(criteria, root, joins);
        query.where(cb.and(scoped, cb.isNotNull(value)));
        query.orderBy(cb.asc(value));

        return entityManager.createQuery(query).getResultList().stream()
                .filter(MovementSearchDao::hasText)
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * The movement's nullable ends, joined once each.
     *
     * <p>Created lazily and cached. Two calls to {@code root.join(...)} for the same association
     * produce two joins, which multiplies rows and inflates the count — and the count is what drives
     * the page numbers, so the table would claim pages that hold nothing.
     */
    private static final class Joins {
        private final Root<Movement> root;
        private final Map<String, Join<?, ?>> cache = new LinkedHashMap<>();

        private Joins(Root<Movement> root) {
            this.root = root;
        }

        private Join<?, ?> of(String path) {
            /*
             * Resolve the parent BEFORE touching the map.
             *
             * This was a computeIfAbsent whose mapping function recursed into of() for the parent
             * path — so "sourceStore.location" re-entered the same HashMap while it was still
             * computing, and HashMap throws ConcurrentModificationException rather than corrupting
             * itself. Every nested path did it, which is every path the branch-scope predicate uses,
             * so the listing worked for a caller who sees all branches and returned a 500 to
             * everybody else.
             */
            Join<?, ?> existing = cache.get(path);
            if (existing != null) return existing;

            int split = path.lastIndexOf('.');
            Join<?, ?> parent = split < 0 ? null : of(path.substring(0, split));
            Join<?, ?> join = parent == null
                    ? root.join(path, JoinType.LEFT)
                    : parent.join(path.substring(split + 1), JoinType.LEFT);

            cache.put(path, join);
            return join;
        }

        Join<?, ?> sourceUser() { return of("sourceUser"); }
        Join<?, ?> receivingOfficer() { return of("receivingOfficer"); }
        Join<?, ?> requestRequester() { return of("request.requester"); }
        Join<?, ?> sourceStore() { return of("sourceStore"); }
        Join<?, ?> destStore() { return of("destStore"); }
        Join<?, ?> recipientUser() { return of("recipientUser"); }
        Join<?, ?> courier() { return of("courier"); }
        Join<?, ?> initiator() { return of("initiator"); }
        Join<?, ?> sourceStoreLocation() { return of("sourceStore.location"); }
        Join<?, ?> destStoreLocation() { return of("destStore.location"); }
        Join<?, ?> sourceUserBranch() { return of("sourceUser.branch"); }
        Join<?, ?> recipientUserBranch() { return of("recipientUser.branch"); }
    }
}
