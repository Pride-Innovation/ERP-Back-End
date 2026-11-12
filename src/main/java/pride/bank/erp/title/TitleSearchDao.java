/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.title;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import pride.bank.erp.department.Department;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static pride.bank.erp.helper.Constants.*;


@Repository
public class TitleSearchDao {
    private final EntityManager entityManager;
    private final CriteriaBuilder criteriaBuilder;

    /**
     * Constructor initializes the TitleSearchDao with an EntityManager.
     *
     * @param entityManager the entity manager used to access the database.
     */
    public TitleSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
    }

    /**
     * Finds all titles with the specified filters and pagination settings.
     *
     * @param titlePage           an object containing pagination and sorting information.
     * @param titleSearchCriteria an object containing search criteria to filter titles.
     * @return a paginated list of titles that match the specified search criteria.
     */
    public Page<Title> findAllWithFilters(
            TitlePage titlePage,
            TitleSearchCriteria titleSearchCriteria
    ) {
        CriteriaQuery<Title> criteriaQuery = criteriaBuilder.createQuery(Title.class);
        Root<Title> titleRoot = criteriaQuery.from(Title.class);

        Predicate predicate = getPredicate(titleSearchCriteria, titleRoot);
        criteriaQuery.where(predicate);

        setOrder(titlePage, criteriaQuery, titleRoot);

        TypedQuery<Title> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(titlePage) * pageSize(titlePage));
        typedQuery.setMaxResults(pageSize(titlePage));

        Pageable pageable = getPageable(titlePage);
        long titlesCount = getTitlesCountMethod(titleSearchCriteria);
        return new PageImpl<>(typedQuery.getResultList(), pageable, titlesCount);
    }

    /**
     * Builds a predicate for filtering titles based on search criteria.
     *
     * @param titleSearchCriteria the criteria to filter titles.
     * @param titleRoot           the root entity in the query.
     * @return a predicate containing the filtering conditions.
     */
    private Predicate getPredicate(TitleSearchCriteria titleSearchCriteria, Root<Title> titleRoot) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(titleSearchCriteria.getName())) {
            predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(titleRoot.get("name")),
                    "%" + titleSearchCriteria.getName().toLowerCase() + "%"
            ));
        }

        if (Objects.nonNull(titleSearchCriteria.getShortCode())) {
            predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(titleRoot.get("shortCode")),
                    "%" + titleSearchCriteria.getShortCode().toLowerCase() + "%"
            ));
        }

        if (Objects.nonNull(titleSearchCriteria.getReportsToId())) {
            predicates.add(criteriaBuilder.equal(titleRoot.get("reportsTo").get("id"), titleSearchCriteria.getReportsToId()));
        }

        if (Objects.nonNull(titleSearchCriteria.getRoleId())) {
            predicates.add(criteriaBuilder.equal(titleRoot.get("role").get("Id"), titleSearchCriteria.getRoleId()));
        }

        if (titleSearchCriteria.isWorkplaceNotSet()) {
            predicates.add(criteriaBuilder.isNull(titleRoot.get("workplace")));
        } else if (titleSearchCriteria.getWorkplace() != null) {
            predicates.add(criteriaBuilder.equal(titleRoot.get("workplace"), titleSearchCriteria.getWorkplace()));
        }

        /*
         * The department is optional, so the id comparison goes through an explicit LEFT JOIN
         * (trap #1). It is created only when the filter needs it, once per query.
         */
        if (titleSearchCriteria.getDepartmentId() != null) {
            Join<Title, Department> department = titleRoot.join("department", JoinType.LEFT);
            predicates.add(criteriaBuilder.equal(department.get("id"), titleSearchCriteria.getDepartmentId()));
        } else if (titleSearchCriteria.isNoDepartment()) {
            predicates.add(criteriaBuilder.isNull(titleRoot.get("department")));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Sets the sorting order for the query based on the properties in titlePage.
     *
     * @param titlePage     contains sorting information.34
     * @param criteriaQuery the criteria query to apply the order.
     * @param titleRoot     the root entity in the criteria query.
     */
    private void setOrder(
            TitlePage titlePage,
            CriteriaQuery<Title> criteriaQuery,
            Root<Title> titleRoot) {
        Sort.Direction sortDirection = sortDirection(titlePage);
        String column = sortBy(titlePage);
        Order primary = sortDirection.equals(Sort.Direction.ASC)
                ? criteriaBuilder.asc(titleRoot.get(column))
                : criteriaBuilder.desc(titleRoot.get(column));
        // Always end in id (trap #3): names repeat, and ties paged with OFFSET repeat and drop titles.
        criteriaQuery.orderBy(column.equals("id")
                ? List.of(primary)
                : List.of(primary, criteriaBuilder.asc(titleRoot.get("id"))));
    }

    /**
     * Creates pagination settings based on titlePage properties.
     *
     * @param titlePage contains pagination information such as page number and size.
     * @return a Pageable instance with pagination and optional sorting settings.
     */
    private Pageable getPageable(TitlePage titlePage) {
        Sort sort = Sort.by(titlePage.getSortDirection(), sortBy(titlePage));
        return PageRequest.of(pageNumber(titlePage), pageSize(titlePage), sort);
    }

    /**
     * Counts the total number of titles that match the search criteria.
     *
     * @param titleSearchCriteria the criteria to filter titles.
     * @return the total count of Titles matching the criteria.
     */
    private long getTitlesCountMethod(TitleSearchCriteria titleSearchCriteria) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Title> countRoot = countQuery.from(Title.class);

        Predicate countPredicate = getPredicate(titleSearchCriteria, countRoot);
        countQuery.select(criteriaBuilder.count(countRoot)).where(countPredicate);

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Determines the sort direction.
     *
     * @param titlePage the criteria to determine the sort direction.
     * @return the sort direction.
     */
    private Sort.Direction sortDirection(TitlePage titlePage) {
        return titlePage.getSortDirection() != null ? titlePage.getSortDirection() : Sort.Direction.ASC;
    }

    /**
     * Methods below determine the sorting options.
     *
     * @param titlePage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    /**
     * Only columns a title has. {@code sortBy} reaches {@code root.get(...)}, which throws on anything
     * else, so a stale link or a typo would otherwise fail the listing outright.
     */
    private static final Set<String> SORTABLE = Set.of("id", "name", "shortCode", "workplace");

    private String sortBy(TitlePage titlePage) {
        String requested = titlePage.getSortBy();
        return requested != null && SORTABLE.contains(requested) ? requested : "id";
    }

    private Integer pageSize(TitlePage titlePage) {
        return titlePage.getPageSize() != null ? titlePage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(TitlePage titlePage) {
        return titlePage.getPageNumber() != null ? titlePage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }
}
