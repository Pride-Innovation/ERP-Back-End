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

        if (Objects.nonNull(requestSearchCriteria.getRequesterIds()) && !requestSearchCriteria.getRequesterIds().isEmpty()) {
            predicates.add(requestRoot.get("requester").get("id").in(requestSearchCriteria.getRequesterIds()));
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

        // Ensure that Admin can review non stationery requests of branches
        if (Objects.nonNull(requestSearchCriteria.getIsBranchNonStationery())) {
            Expression<Long> branchIdPath = requestRoot.get("requester").get("branch").get("id");

            Predicate isHeadOffice = criteriaBuilder.equal(branchIdPath, 1L);

            Predicate isBranchNonStationery = criteriaBuilder.equal(
                    requestRoot.get("isBranchNonStationery"),
                    requestSearchCriteria.getIsBranchNonStationery()
            );

            predicates.add(criteriaBuilder.or(isHeadOffice, isBranchNonStationery));
        }

        // Ensure that BOM can review stationery requests of their branches
        if (Objects.nonNull(requestSearchCriteria.getIsBranchStationery())) {
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

            Predicate firstNameMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(requestRoot.get("currentApprover").get("firstName")), searchName
            );

            Predicate lastNameMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(requestRoot.get("currentApprover").get("lastName")), searchName
            );

            Predicate otherNameMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(requestRoot.get("currentApprover").get("otherName")), searchName
            );

            predicates.add(criteriaBuilder.or(firstNameMatch, lastNameMatch, otherNameMatch));
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
        Sort.Direction sortDirection = sortDirection(requestPage) != null ? sortDirection(requestPage)
                : Sort.Direction.ASC;
        if (sortDirection.equals(Sort.Direction.ASC)) {
            criteriaQuery.orderBy(criteriaBuilder.asc(requestRoot.get(sortBy(requestPage))));
        } else {
            criteriaQuery.orderBy(criteriaBuilder.desc(requestRoot.get(sortBy(requestPage))));
        }
    }

    /**
     * Creates pagination settings based on RequestPage properties.
     *
     * @param requestPage contains pagination information such as page number and size.
     * @return a Pageable instance with pagination and optional sorting settings.
     */
    private Pageable getPageable(RequestPage requestPage) {
        Sort sort = Sort.by(requestPage.getSortDirection(), sortBy(requestPage));
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
    private Sort.Direction sortDirection(RequestPage requestPage) {
        return requestPage.getSortDirection() != null ? requestPage.getSortDirection() : Sort.Direction.ASC;
    }

    /**
     * Methods below determine the sorting options.
     *
     * @param requestPage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    private String sortBy(RequestPage requestPage) {
        return requestPage.getSortBy() != null ? requestPage.getSortBy() : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(RequestPage requestPage) {
        return requestPage.getPageSize() != null ? requestPage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(RequestPage requestPage) {
        return requestPage.getPageNumber() != null ? requestPage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }
}
