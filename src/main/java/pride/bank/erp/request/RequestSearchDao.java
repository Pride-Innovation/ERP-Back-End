/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.request;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static pride.bank.erp.helper.Constants.*;


@Repository
public class RequestSearchDao {
    private final EntityManager entityManager;
    private final CriteriaBuilder criteriaBuilder;

    /**
     * Constructor initializes the RequestSearchDao with an EntityManager.
     *
     * @param entityManager the entity manager used to access the database.
     */
    public RequestSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
    }


    /**
     * Finds all requests with the specified filters and pagination settings.
     *
     * @param requestPage           an object containing pagination and sorting information.
     * @param requestSearchCriteria an object containing search criteria to filter requests.
     * @return a paginated list of requests that match the specified search criteria.
     */
    public Page<Request> findAllWithFilters(
            RequestPage requestPage,
            RequestSearchCriteria requestSearchCriteria
    ) {
        CriteriaQuery<Request> criteriaQuery = criteriaBuilder.createQuery(Request.class);
        Root<Request> requestRoot = criteriaQuery.from(Request.class);

        Predicate predicate = getPredicate(requestSearchCriteria, requestRoot);
        criteriaQuery.where(predicate);

        setOrder(requestPage, criteriaQuery, requestRoot);

        TypedQuery<Request> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(requestPage) * pageSize(requestPage));
        typedQuery.setMaxResults(pageSize(requestPage));

        Pageable pageable = getPageable(requestPage);
        long requestsCount = getRequestsCountMethod(requestSearchCriteria);
        return new PageImpl<>(typedQuery.getResultList(), pageable, requestsCount);
    }

    /**
     * Builds a predicate for filtering requests based on search criteria.
     *
     * @param requestSearchCriteria the criteria to filter requests.
     * @param requestRoot           the root entity in the query.
     * @return a predicate containing the filtering conditions.
     */
    private Predicate getPredicate(RequestSearchCriteria requestSearchCriteria, Root<Request> requestRoot) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(requestSearchCriteria.getName())) {
            predicates.add(criteriaBuilder.like(requestRoot.get("name"), "%" + requestSearchCriteria.getName() + "%"));
        }

        if (Objects.nonNull(requestSearchCriteria.getPriority())) {
            predicates.add(criteriaBuilder.like(requestRoot.get("priority"), "%" + requestSearchCriteria.getPriority() + "%"));
        }

        if (Objects.nonNull(requestSearchCriteria.getDescription())) {
            predicates.add(criteriaBuilder.like(requestRoot.get("description"), "%" + requestSearchCriteria.getDescription() + "%"));
        }

        if (Objects.nonNull(requestSearchCriteria.getTimeOfSubmissionOfRequest())) {
            predicates.add(criteriaBuilder.like(requestRoot.get("timeOfSubmissionOfRequest"), "%" + requestSearchCriteria.getTimeOfSubmissionOfRequest() + "%"));
        }

        if (Objects.nonNull(requestSearchCriteria.getStatusIds()) && !requestSearchCriteria.getStatusIds().isEmpty()) {
            predicates.add(requestRoot.get("status").get("id").in(requestSearchCriteria.getStatusIds()));
        }

        // The role-based subordinate restriction (requesterIds) is for the general
        // listings. When an explicit approver is targeted — the approval inbox passes
        // currentApproverId — visibility must be governed solely by "are you the
        // assigned approver?", because the workflow engine already decided who must act.
        // Layering the subordinate restriction on top would hide a request from an
        // approver who sits more than one title-level above the requester (e.g. a HoD,
        // or a manager above a supervisor), so skip it whenever currentApproverId is set.
        if (Objects.isNull(requestSearchCriteria.getCurrentApproverId())
                && Objects.nonNull(requestSearchCriteria.getRequesterIds())
                && !requestSearchCriteria.getRequesterIds().isEmpty()) {
            predicates.add(requestRoot.get("requester").get("id").in(requestSearchCriteria.getRequesterIds()));
        }

        if (Objects.nonNull(requestSearchCriteria.getExcludedStatusIds())
                && !requestSearchCriteria.getExcludedStatusIds().isEmpty()) {
            // "Still open" — see RequestSearchCriteria on why this is expressed as an exclusion.
            predicates.add(criteriaBuilder.not(
                    requestRoot.get("status").get("id").in(requestSearchCriteria.getExcludedStatusIds())));
        }

        if (Objects.nonNull(requestSearchCriteria.getRaisedByUserId())) {
            predicates.add(criteriaBuilder.equal(
                    reuseJoin(requestRoot, "requester").get("id"),
                    requestSearchCriteria.getRaisedByUserId()));
        }

        if (Objects.nonNull(requestSearchCriteria.getRequesterBranchId())) {
            // Through the requester's branch: a request has no branch of its own, it belongs to
            // wherever the person who raised it works. LEFT joined so a requester with no duty
            // station does not silently disappear from a national count.
            Join<Object, Object> requesterBranch =
                    reuseJoin(reuseJoin(requestRoot, "requester"), "branch");
            predicates.add(criteriaBuilder.equal(
                    requesterBranch.get("id"), requestSearchCriteria.getRequesterBranchId()));
        }

        if (Objects.nonNull(requestSearchCriteria.getDeleted())) {
            predicates.add(criteriaBuilder.equal(requestRoot.get("deleted"), requestSearchCriteria.getDeleted()));
        }

        if (Objects.nonNull(requestSearchCriteria.getStartDate())) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(requestRoot.get("createDate"), requestSearchCriteria.getStartDate()));
        }

        if (Objects.nonNull(requestSearchCriteria.getEndDate())) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(requestRoot.get("createDate"), requestSearchCriteria.getEndDate()));
        }

        // Legacy stationery / non-stationery scope rules. These predate the workflow engine
        // and were the only way admins/BOMs got visibility into branch requests. Now that the
        // engine can route any branch request to a specific approver via currentApprover, those
        // rules wrongly hide assigned requests from the approver (e.g. a BOM listed as the
        // currentApprover for a branch Computers request — non-stationery — would never see it
        // because neither side of `requester.branch=HO OR isBranchStationery=true` is true).
        // Skip both gates when the inbox is explicitly targeting an approver — the engine's
        // assignment is the authority in that case.

        // Ensure that Admin can review non stationery requests of branches
        if (Objects.isNull(requestSearchCriteria.getCurrentApproverId())
                && Objects.nonNull(requestSearchCriteria.getIsBranchNonStationery())) {
            Expression<Long> branchIdPath = requestRoot.get("requester").get("branch").get("id");

            Predicate isHeadOffice = criteriaBuilder.equal(branchIdPath, 1L);

            Predicate isBranchNonStationery = criteriaBuilder.equal(
                    requestRoot.get("isBranchNonStationery"),
                    requestSearchCriteria.getIsBranchNonStationery()
            );

            predicates.add(criteriaBuilder.or(isHeadOffice, isBranchNonStationery));
        }

        // Ensure that BOM can review stationery requests of their branches
        if (Objects.isNull(requestSearchCriteria.getCurrentApproverId())
                && Objects.nonNull(requestSearchCriteria.getIsBranchStationery())) {
            Expression<Long> branchIdPath = requestRoot.get("requester").get("branch").get("id");

            Predicate isHeadOffice = criteriaBuilder.equal(branchIdPath, 1L);

            Predicate isBranchStationery = criteriaBuilder.equal(
                    requestRoot.get("isBranchStationery"),
                    requestSearchCriteria.getIsBranchStationery()
            );


            // If it's Head Office, ignore the filter by always returning true (i.e., include it)
            // If it's not Head Office, apply the filter
            predicates.add(criteriaBuilder.or(isHeadOffice, isBranchStationery)
            );
        }

        if (Objects.nonNull(requestSearchCriteria.getRequestedBy())) {
            String searchName = "%" + requestSearchCriteria.getRequestedBy().toLowerCase() + "%";

            Predicate firstNameMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(requestRoot.get("requester").get("firstName")), searchName
            );

            Predicate lastNameMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(requestRoot.get("requester").get("lastName")), searchName
            );

            Predicate otherNameMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(requestRoot.get("requester").get("otherName")), searchName
            );

            predicates.add(criteriaBuilder.or(firstNameMatch, lastNameMatch, otherNameMatch));
        }

        if (Objects.nonNull(requestSearchCriteria.getApprover())) {
            String searchName = "%" + requestSearchCriteria.getApprover().toLowerCase() + "%";

            /*
             * An explicit LEFT join, not path navigation.
             *
             * `currentApprover` is nullable — a request that has finished its ladder, or been
             * rejected, has none. Reaching through it with requestRoot.get("currentApprover") builds
             * an INNER join, which filters those rows out in the FROM clause before the name
             * comparison is even reached. That is invisible here, but it means the result set is
             * quietly narrower than the filter describes.
             */
            Join<Object, Object> approver = reuseJoin(requestRoot, "currentApprover");

            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(approver.get("firstName")), searchName),
                    criteriaBuilder.like(criteriaBuilder.lower(approver.get("lastName")), searchName),
                    criteriaBuilder.like(criteriaBuilder.lower(approver.get("otherName")), searchName)
            ));
        }

        if (Objects.nonNull(requestSearchCriteria.getCurrentApproverId())) {
            /*
             * The approval inbox: what is assigned to me personally, OR routed to a unit I belong to
             * (group-email steps set currentUnit instead of an individual).
             *
             * Both sides go through explicit LEFT joins. They were dotted paths, which JPA turns
             * into INNER joins that sit in the FROM clause and filter before the OR is ever
             * evaluated — so asking for "assigned to me or to my unit" silently required a request
             * to have BOTH a current approver and a current unit. A request routed to one named
             * approver, which is the ordinary case, has no currentUnit and was therefore excluded
             * from the very inbox built to show it.
             */
            Join<Object, Object> approver = reuseJoin(requestRoot, "currentApprover");
            Predicate byApprover = criteriaBuilder.equal(
                    approver.get("id"), requestSearchCriteria.getCurrentApproverId());

            if (Objects.nonNull(requestSearchCriteria.getCurrentUnitId())) {
                Join<Object, Object> unit = reuseJoin(requestRoot, "currentUnit");
                Predicate byUnit = criteriaBuilder.equal(
                        unit.get("id"), requestSearchCriteria.getCurrentUnitId());
                predicates.add(criteriaBuilder.or(byApprover, byUnit));
            } else {
                predicates.add(byApprover);
            }
        }

        // Filter Admin data for the requests listing

        if (Boolean.TRUE.equals(requestSearchCriteria.getIsAdmin()) &&
                Objects.nonNull(requestSearchCriteria.getCurrentUserId())) {

            Path<Long> requesterIdPath = requestRoot.get("requester").get("id");
            Path<String> statusNamePath = requestRoot.get("status").get("status"); // Adjust if field is different

            Predicate ownRequests = criteriaBuilder.equal(requesterIdPath, requestSearchCriteria.getCurrentUserId());

            // Exclude specific statuses for other users
            Predicate notOwnRequests = criteriaBuilder.notEqual(requesterIdPath, requestSearchCriteria.getCurrentUserId());
            Predicate statusNotRequestCreated = criteriaBuilder.notEqual(statusNamePath, "requestCreated");
            Predicate statusNotRequestRejected = criteriaBuilder.notEqual(statusNamePath, "requestRejected");

            Predicate otherUsersValidStatuses = criteriaBuilder.and(
                    notOwnRequests,
                    statusNotRequestCreated,
                    statusNotRequestRejected
            );

            // Combine own requests and filtered other users' requests
            predicates.add(criteriaBuilder.or(ownRequests, otherUsersValidStatuses));
        }


        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Sets the sorting order for the query based on the properties in RequestPage.
     *
     * @param requestPage   contains sorting information.34
     * @param criteriaQuery the criteria query to apply the order.
     * @param requestRoot   the root entity in the criteria query.
     */
    private void setOrder(RequestPage requestPage, CriteriaQuery<Request> criteriaQuery, Root<Request> requestRoot) {
        String field = sortBy(requestPage);
        boolean ascending = sortDirection(requestPage).equals(Sort.Direction.ASC);

        Order primary = ascending
                ? criteriaBuilder.asc(requestRoot.get(field))
                : criteriaBuilder.desc(requestRoot.get(field));

        /*
         * A tie-break on the primary key, always.
         *
         * Ordering on a single non-unique column leaves ties, and MySQL may return tied rows in a
         * different order on each query. With OFFSET/LIMIT paging that means the same request can
         * appear on two pages while another appears on none — the list looks like it is losing
         * records. Sorting by priority, where there are three distinct values across the whole
         * table, makes it near-certain.
         */
        if (DEFAULT_SORT_FIELD.equals(field)) {
            criteriaQuery.orderBy(primary);
            return;
        }
        Order tieBreak = ascending
                ? criteriaBuilder.asc(requestRoot.get(DEFAULT_SORT_FIELD))
                : criteriaBuilder.desc(requestRoot.get(DEFAULT_SORT_FIELD));
        criteriaQuery.orderBy(primary, tieBreak);
    }

    /**
     * The existing LEFT join for an attribute, or a new one.
     *
     * <p>{@code From#join} adds a join every time it is called, so two predicates asking for the
     * same association would put it in the FROM clause twice and multiply the rows — and with them
     * the count.
     */
    @SuppressWarnings("unchecked")
    private Join<Object, Object> reuseJoin(From<?, ?> from, String attribute) {
        return (Join<Object, Object>) from.getJoins().stream()
                .filter(j -> attribute.equals(j.getAttribute().getName()))
                .findFirst()
                .orElseGet(() -> (Join) from.join(attribute, JoinType.LEFT));
    }

    /**
     * Creates pagination settings based on RequestPage properties.
     *
     * @param requestPage contains pagination information such as page number and size.
     * @return a Pageable instance with pagination and optional sorting settings.
     */
    private Pageable getPageable(RequestPage requestPage) {
        // Through the helper, not the raw getter: Sort.by(null, ...) throws, so an unspecified
        // direction would have taken the whole listing down rather than defaulting.
        Sort sort = Sort.by(sortDirection(requestPage), sortBy(requestPage));
        return PageRequest.of(pageNumber(requestPage), pageSize(requestPage), sort);
    }


    /**
     * Counts the total number of requests that match the search criteria.
     *
     * @param requestSearchCriteria the criteria to filter requests.
     * @return the total count of Requests matching the criteria.
     */
    private long getRequestsCountMethod(RequestSearchCriteria requestSearchCriteria) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Request> countRoot = countQuery.from(Request.class);

        Predicate countPredicate = getPredicate(requestSearchCriteria, countRoot);
        countQuery.select(criteriaBuilder.count(countRoot)).where(countPredicate);

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Determines the sort direction.
     *
     * @param requestPage the criteria to determine the sort direction.
     * @return the sort direction.
     */
    /**
     * Newest-first when nothing is asked for, matching the controller's own default.
     *
     * <p>This fell back to ASC while {@code RequestController} defaults to DESC, so the DAO and the
     * endpoint in front of it disagreed on what "unspecified" meant — harmless only because the
     * controller always sends a value, and a trap for any caller that did not.
     */
    private Sort.Direction sortDirection(RequestPage requestPage) {
        return requestPage.getSortDirection() != null ? requestPage.getSortDirection() : Sort.Direction.DESC;
    }

    /**
     * Methods below determine the sorting options.
     *
     * @param requestPage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    /**
     * Columns a caller may sort on.
     *
     * <p>Whitelisted because the value goes straight into {@code requestRoot.get(...)}: anything
     * that is not a mapped attribute throws {@link IllegalArgumentException} and the request becomes
     * a 500. Associations are excluded — sorting by one would need a join this method cannot add.
     */
    private static final java.util.Set<String> SORTABLE = java.util.Set.of(
            "id", "name", "priority", "description", "createDate", "lastModified",
            "timeOfSubmissionOfRequest"
    );

    private String sortBy(RequestPage requestPage) {
        String requested = requestPage.getSortBy();
        // An unrecognised column falls back rather than failing: a stale bookmark or a renamed field
        // should still show the list, in a defined order, instead of an error page.
        return (requested != null && SORTABLE.contains(requested)) ? requested : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(RequestPage requestPage) {
        return requestPage.getPageSize() != null ? requestPage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(RequestPage requestPage) {
        return requestPage.getPageNumber() != null ? requestPage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }
}
