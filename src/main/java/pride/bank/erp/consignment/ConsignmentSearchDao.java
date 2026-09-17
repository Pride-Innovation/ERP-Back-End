package pride.bank.erp.consignment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Filtering, sorting and paging for consignments — in the database, not the browser.
 *
 * <h2>What this replaces</h2>
 * The page fetched the first hundred journeys once and did the rest client-side: ten filters, the
 * tabs, their counts, the dropdown options and three exports. The endpoint declared two parameters
 * and applied them in a ternary — {@code status != null ? findByStatus : branchId != null ?
 * findForBranch : findAll} — so choosing a status <b>silently discarded the branch</b>, and choosing
 * neither returned every consignment in the bank to anyone holding {@code READ_MOVEMENT}.
 *
 * <p>That ternary is worth naming as its own trap: it is not the undeclared-parameter one, because
 * both parameters were declared. It is a parameter accepted, documented, and then dropped by the
 * control flow — which looks even more correct from the outside.
 *
 * <h2>Written against the same three traps as {@code MovementSearchDao}</h2>
 * <ul>
 *   <li><b>Explicit LEFT JOINs, each created once.</b> Both ends of a journey and its courier are
 *       nullable; a dotted path would inner-join and drop rows in the FROM clause. Joins are cached
 *       because a second {@code join()} on the same association duplicates it and inflates the count,
 *       which is what drives the page numbers.</li>
 *   <li><b>The sort always ends in {@code id}</b>, so paging over a non-unique column cannot repeat
 *       or drop rows.</li>
 *   <li><b>The count query builds its own root and joins</b>, rather than counting through the select
 *       query's.</li>
 * </ul>
 */
@Repository
public class ConsignmentSearchDao {

    private final EntityManager entityManager;
    private final CriteriaBuilder cb;

    public ConsignmentSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.cb = entityManager.getCriteriaBuilder();
    }

    /** One page of consignments matching the criteria, newest first unless told otherwise. */
    public Page<Consignment> search(ConsignmentSearchCriteria criteria, Pageable pageable) {
        CriteriaQuery<Consignment> query = cb.createQuery(Consignment.class);
        Root<Consignment> root = query.from(Consignment.class);
        Joins joins = new Joins(root);

        query.where(predicate(criteria, root, joins));
        query.distinct(true);
        order(query, root, pageable);

        TypedQuery<Consignment> typed = entityManager.createQuery(query);
        typed.setFirstResult((int) pageable.getOffset());
        typed.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(typed.getResultList(), pageable, count(criteria));
    }

    /**
     * How many journeys sit in each status, across everything the criteria match.
     *
     * <p>Feeds the page's status tabs. They counted the rows the browser held, so past the fetch size
     * they described a slice rather than the register.
     */
    public Map<ConsignmentStatus, Long> countByStatus(ConsignmentSearchCriteria criteria) {
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Consignment> root = query.from(Consignment.class);
        Joins joins = new Joins(root);

        Path<ConsignmentStatus> status = root.get("status");
        query.where(predicate(criteria, root, joins));
        query.multiselect(status, cb.countDistinct(root.get("id")));
        query.groupBy(status);

        Map<ConsignmentStatus, Long> counts = new LinkedHashMap<>();
        for (Object[] row : entityManager.createQuery(query).getResultList()) {
            counts.put((ConsignmentStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * The distinct values behind the panel's three dropdowns.
     *
     * <p>Derived in the browser from the loaded rows before this, so the panel could not offer a
     * value it would have matched. Only the scope is applied — narrowing the options by the active
     * filters would empty one list as soon as another was chosen, and the panel could never be
     * widened again.
     */
    public FilterOptions filterOptions(Long scopedBranchId) {
        ConsignmentSearchCriteria scopeOnly = ConsignmentSearchCriteria.builder()
                .scopedBranchId(scopedBranchId).build();

        return new FilterOptions(
                distinct(scopeOnly, (root, joins) -> joins.sourceLocation().get("name")),
                distinct(scopeOnly, (root, joins) -> joins.destLocation().get("name")),
                distinct(scopeOnly, this::courierLabel));
    }

    /** The option lists behind the source / destination / courier dropdowns. */
    public record FilterOptions(List<String> sources, List<String> destinations,
                                List<String> couriers) {
    }

    // ── predicate ───────────────────────────────────────────────────────────

    private Predicate predicate(ConsignmentSearchCriteria c, Root<Consignment> root, Joins joins) {
        List<Predicate> predicates = new ArrayList<>();

        /*
         * Dated on the journey where there is one, else on when it was opened.
         *
         * A draft has no dispatch date, so dating purely on that column would drop every draft from a
         * date-filtered view — and drafts are exactly what someone filtering by "this week" is often
         * looking for. Coalesce matches the panel, which reads `dispatchDate ?? createDate`.
         */
        Expression<LocalDateTime> timeline =
                cb.coalesce(root.get("dispatchDate"), root.get("createDate"));

        if (Objects.nonNull(c.getDateFrom())) {
            predicates.add(cb.greaterThanOrEqualTo(timeline, c.getDateFrom()));
        }
        if (Objects.nonNull(c.getDateTo())) {
            predicates.add(cb.lessThanOrEqualTo(timeline, c.getDateTo()));
        }
        if (c.getStatuses() != null && !c.getStatuses().isEmpty()) {
            predicates.add(root.get("status").in(c.getStatuses()));
        }
        if (hasText(c.getSource())) {
            predicates.add(cb.equal(joins.sourceLocation().get("name"), c.getSource()));
        }
        if (hasText(c.getDestination())) {
            predicates.add(cb.equal(joins.destLocation().get("name"), c.getDestination()));
        }
        if (hasText(c.getCourier())) {
            predicates.add(cb.equal(courierLabel(root, joins), c.getCourier()));
        }
        if (hasText(c.getTrackingNumber())) {
            predicates.add(cb.like(cb.lower(root.get("trackingNumber")),
                    "%" + c.getTrackingNumber().toLowerCase() + "%"));
        }
        if (hasText(c.getPlateNumber())) {
            predicates.add(cb.like(cb.lower(root.get("plateNumber")),
                    "%" + c.getPlateNumber().toLowerCase() + "%"));
        }
        if (hasText(c.getTimeliness())) {
            predicates.add(timelinessPredicate(root, c.getTimeliness()));
        }
        if (hasText(c.getLoad())) {
            // A journey with nothing on it cannot be dispatched, so "empty" is how the drafts that
            // need attention are found.
            Expression<Integer> loaded = cb.size(root.get("movements"));
            predicates.add("empty".equalsIgnoreCase(c.getLoad())
                    ? cb.equal(loaded, 0)
                    : cb.greaterThan(loaded, 0));
        }

        /*
         * Scope: either end of the journey being the caller's branch puts it in reach.
         *
         * Mirrors ConsignmentRepository#findForBranch, which already had this rule and was only ever
         * reached when the client asked for a branch by hand.
         */
        if (Objects.nonNull(c.getScopedBranchId())) {
            Long branch = c.getScopedBranchId();
            predicates.add(cb.or(
                    cb.equal(joins.sourceLocation().get("id"), branch),
                    cb.equal(joins.destLocation().get("id"), branch)));
        }

        return predicates.isEmpty()
                ? cb.conjunction()
                : cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Late, or not.
     *
     * <p>Only a journey actually on the road can be late: a draft has not left and a received one has
     * arrived, so neither is overdue however old its expected date. "On time" is therefore the
     * negation of the whole condition rather than of the date alone — otherwise every draft would be
     * reported as running late.
     */
    private Predicate timelinessPredicate(Root<Consignment> root, String timeliness) {
        Predicate onTheRoad = root.get("status").in(
                ConsignmentStatus.DISPATCHED, ConsignmentStatus.IN_TRANSIT);
        Predicate pastDue = cb.and(
                cb.isNotNull(root.get("expectedDeliveryDate")),
                cb.lessThan(root.get("expectedDeliveryDate"), LocalDateTime.now()));
        Predicate overdue = cb.and(onTheRoad, pastDue);

        return "overdue".equalsIgnoreCase(timeliness) ? overdue : cb.not(overdue);
    }

    /**
     * The courier as the page names it.
     *
     * <p>Free-text service first, then the courier record — the <b>opposite</b> precedence from
     * movements, and deliberately so: this mirrors the page's own {@code consignmentCourierName}. If
     * the two disagreed, the dropdown would offer a name the filter could not match.
     */
    private Expression<String> courierLabel(Root<Consignment> root, Joins joins) {
        return cb.coalesce(root.get("courierService"), joins.courier().get("name"));
    }

    // ── count, ordering, distinct ────────────────────────────────────────────

    private long count(ConsignmentSearchCriteria criteria) {
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Consignment> root = query.from(Consignment.class);
        Joins joins = new Joins(root);

        query.select(cb.countDistinct(root.get("id")));
        query.where(predicate(criteria, root, joins));

        Long total = entityManager.createQuery(query).getSingleResult();
        return total != null ? total : 0L;
    }

    /** Sort as asked, then always by id, so a page boundary always falls in the same place. */
    private void order(CriteriaQuery<Consignment> query, Root<Consignment> root, Pageable pageable) {
        List<Order> orders = new ArrayList<>();

        for (Sort.Order sort : pageable.getSort()) {
            if ("id".equals(sort.getProperty())) continue;
            orders.add(sort.isAscending()
                    ? cb.asc(root.get(sort.getProperty()))
                    : cb.desc(root.get(sort.getProperty())));
        }

        boolean ascendingTail = pageable.getSort().stream()
                .filter(o -> !"id".equals(o.getProperty()))
                .reduce((first, second) -> second)
                .map(Sort.Order::isAscending)
                .orElse(false);

        orders.add(ascendingTail ? cb.asc(root.get("id")) : cb.desc(root.get("id")));
        query.orderBy(orders);
    }

    /** Distinct non-blank values of one expression, ordered, for a dropdown. */
    private List<String> distinct(ConsignmentSearchCriteria criteria,
                                  java.util.function.BiFunction<Root<Consignment>, Joins, Expression<String>> of) {
        CriteriaQuery<String> query = cb.createQuery(String.class);
        Root<Consignment> root = query.from(Consignment.class);
        Joins joins = new Joins(root);

        Expression<String> value = of.apply(root, joins);
        query.select(value).distinct(true);
        query.where(cb.and(predicate(criteria, root, joins), cb.isNotNull(value)));
        query.orderBy(cb.asc(value));

        return entityManager.createQuery(query).getResultList().stream()
                .filter(ConsignmentSearchDao::hasText)
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * The journey's nullable associations, joined once each.
     *
     * <p>Resolved without {@code computeIfAbsent}: a mapping function that recursed to build a parent
     * path re-entered the same map and threw {@code ConcurrentModificationException}, which is how
     * the movements register shipped broken for every branch-scoped caller. Same shape, so the same
     * care.
     */
    private static final class Joins {
        private final Root<Consignment> root;
        private final Map<String, Join<?, ?>> cache = new LinkedHashMap<>();

        private Joins(Root<Consignment> root) {
            this.root = root;
        }

        private Join<?, ?> of(String path) {
            Join<?, ?> existing = cache.get(path);
            if (existing != null) return existing;

            Join<?, ?> join = root.join(path, JoinType.LEFT);
            cache.put(path, join);
            return join;
        }

        Join<?, ?> sourceLocation() { return of("sourceLocation"); }
        Join<?, ?> destLocation() { return of("destLocation"); }
        Join<?, ?> courier() { return of("courier"); }
    }
}
