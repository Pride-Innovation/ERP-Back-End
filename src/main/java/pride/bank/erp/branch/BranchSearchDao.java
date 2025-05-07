package pride.bank.erp.branch;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static pride.bank.erp.helper.Constants.*;

@Repository
public class BranchSearchDao {
    private final EntityManager entityManager;
    private final CriteriaBuilder criteriaBuilder;

    /**
     * Constructor initializes the BranchSearchDao with an EntityManager.
     *
     * @param entityManager the entity manager used to access the database.
     */
    public BranchSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
    }

    /**
     * Finds all branches with the specified filters and pagination settings.
     *
     * @param branchPage           an object containing pagination and sorting information.
     * @param branchSearchCriteria an object containing search criteria to filter branches.
     * @return a paginated list of branches that match the specified search criteria.
     */
    public Page<Branch> findAllWithFilters(
            BranchPage branchPage,
            BranchSearchCriteria branchSearchCriteria
    ) {
        CriteriaQuery<Branch> criteriaQuery = criteriaBuilder.createQuery(Branch.class);
        Root<Branch> branchRoot = criteriaQuery.from(Branch.class);

        Predicate predicate = getPredicate(branchSearchCriteria, branchRoot);
        criteriaQuery.where(predicate);

        setOrder(branchPage, criteriaQuery, branchRoot);

        TypedQuery<Branch> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(branchPage) * pageSize(branchPage));
        typedQuery.setMaxResults(pageSize(branchPage));

        Pageable pageable = getPageable(branchPage);
        long branchesCount = getBranchesCountMethod(branchSearchCriteria);
        return new PageImpl<>(typedQuery.getResultList(), pageable, branchesCount);
    }

    /**
     * Builds a predicate for filtering branches based on search criteria.
     *
     * @param branchSearchCriteria the criteria to filter branches.
     * @param branchRoot           the root entity in the query.
     * @return a predicate containing the filtering conditions.
     */
    private Predicate getPredicate(BranchSearchCriteria branchSearchCriteria, Root<Branch> branchRoot) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(branchSearchCriteria.getName())) {
            predicates.add(criteriaBuilder.like(branchRoot.get("name"), "%" + branchSearchCriteria.getName() + "%"));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Sets the sorting order for the query based on the properties in BranchPage.
     *
     * @param branchPage    contains sorting information.34
     * @param criteriaQuery the criteria query to apply the order.
     * @param branchRoot    the root entity in the criteria query.
     */
    private void setOrder(
            BranchPage branchPage,
            CriteriaQuery<Branch> criteriaQuery,
            Root<Branch> branchRoot) {
        Sort.Direction sortDirection = sortDirection(branchPage) != null ? sortDirection(branchPage)
                : Sort.Direction.ASC;
        if (sortDirection.equals(Sort.Direction.ASC)) {
            criteriaQuery.orderBy(criteriaBuilder.asc(branchRoot.get(sortBy(branchPage))));
        } else {
            criteriaQuery.orderBy(criteriaBuilder.desc(branchRoot.get(sortBy(branchPage))));
        }
    }

    /**
     * Creates pagination settings based on BranchPage properties.
     *
     * @param branchPage contains pagination information such as page number and size.
     * @return a Pageable instance with pagination and optional sorting settings.
     */
    private Pageable getPageable(BranchPage branchPage) {
        Sort sort = Sort.by(branchPage.getSortDirection(), sortBy(branchPage));
        return PageRequest.of(pageNumber(branchPage), pageSize(branchPage), sort);
    }

    /**
     * Counts the total number of branches that match the search criteria.
     *
     * @param branchSearchCriteria the criteria to filter branches.
     * @return the total count of Branches matching the criteria.
     */
    private long getBranchesCountMethod(BranchSearchCriteria branchSearchCriteria) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Branch> countRoot = countQuery.from(Branch.class);

        Predicate countPredicate = getPredicate(branchSearchCriteria, countRoot);
        countQuery.select(criteriaBuilder.count(countRoot)).where(countPredicate);

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Determines the sort direction.
     *
     * @param branchPage the criteria to determine the sort direction.
     * @return the sort direction.
     */
    private Sort.Direction sortDirection(BranchPage branchPage) {
        return branchPage.getSortDirection() != null ? branchPage.getSortDirection() : Sort.Direction.ASC;
    }

    /**
     * Methods below determine the sorting options.
     *
     * @param branchPage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    private String sortBy(BranchPage branchPage) {
        return branchPage.getSortBy() != null ? branchPage.getSortBy() : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(BranchPage branchPage) {
        return branchPage.getPageSize() != null ? branchPage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(BranchPage branchPage) {
        return branchPage.getPageNumber() != null ? branchPage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }
}
