package pride.bank.erp.supplier;

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
public class SupplierSearchDao {
    private final EntityManager entityManager;
    private final CriteriaBuilder criteriaBuilder;

    /**
     * Constructor initializes the SupplierSearchDao with an EntityManager.
     *
     * @param entityManager the entity manager used to access the database.
     */
    public SupplierSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
    }

    /**
     * Finds all suppliers with the specified filters and pagination settings.
     *
     * @param supplierPage           an object containing pagination and sorting information.
     * @param supplierSearchCriteria an object containing search criteria to filter suppliers.
     * @return a paginated list of suppliers that match the specified search criteria.
     */
    public Page<Supplier> findAllWithFilters(
            SupplierPage supplierPage,
            SupplierSearchCriteria supplierSearchCriteria
    ) {
        CriteriaQuery<Supplier> criteriaQuery = criteriaBuilder.createQuery(Supplier.class);
        Root<Supplier> supplierRoot = criteriaQuery.from(Supplier.class);

        Predicate predicate = getPredicate(supplierSearchCriteria, supplierRoot);
        criteriaQuery.where(predicate);

        setOrder(supplierPage, criteriaQuery, supplierRoot);

        TypedQuery<Supplier> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(supplierPage) * pageSize(supplierPage));
        typedQuery.setMaxResults(pageSize(supplierPage));

        Pageable pageable = getPageable(supplierPage);
        long suppliersCount = getSuppliersCountMethod(supplierSearchCriteria);
        return new PageImpl<>(typedQuery.getResultList(), pageable, suppliersCount);
    }

    /**
     * Builds a predicate for filtering suppliers based on search criteria.
     *
     * @param supplierSearchCriteria the criteria to filter suppliers.
     * @param supplierRoot           the root entity in the query.
     * @return a predicate containing the filtering conditions.
     */
    private Predicate getPredicate(SupplierSearchCriteria supplierSearchCriteria, Root<Supplier> supplierRoot) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(supplierSearchCriteria.getName())) {
            predicates.add(criteriaBuilder.like(supplierRoot.get("name"), "%" + supplierSearchCriteria.getName() + "%"));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Sets the sorting order for the query based on the properties in SupplierPage.
     *
     * @param supplierPage  contains sorting information.34
     * @param criteriaQuery the criteria query to apply the order.
     * @param supplierRoot  the root entity in the criteria query.
     */
    private void setOrder(SupplierPage supplierPage, CriteriaQuery<Supplier> criteriaQuery, Root<Supplier> supplierRoot) {
        if (sortDirection(supplierPage).equals(Sort.Direction.ASC)) {
            criteriaQuery.orderBy(criteriaBuilder.asc(supplierRoot.get(sortBy(supplierPage))));
        } else {
            criteriaQuery.orderBy(criteriaBuilder.desc(supplierRoot.get(sortBy(supplierPage))));
        }
    }

    /**
     * Creates pagination settings based on SupplierPage properties.
     *
     * @param supplierPage contains pagination information such as page number and size.
     * @return a Pageable instance with pagination and optional sorting settings.
     */
    private Pageable getPageable(SupplierPage supplierPage) {
        Sort sort = Sort.by(sortDirection(supplierPage), sortBy(supplierPage));
        return PageRequest.of(pageNumber(supplierPage), pageSize(supplierPage), sort);
    }

    /**
     * Counts the total number of suppliers that match the search criteria.
     *
     * @param supplierSearchCriteria the criteria to filter suppliers.
     * @return the total count of suppliers matching the criteria.
     */
    private long getSuppliersCountMethod(SupplierSearchCriteria supplierSearchCriteria) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Supplier> countRoot = countQuery.from(Supplier.class);

        Predicate countPredicate = getPredicate(supplierSearchCriteria, countRoot);
        countQuery.select(criteriaBuilder.count(countRoot)).where(countPredicate);

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Determines the sort direction.
     *
     * @param supplierPage the criteria to determine the sort direction.
     * @return the sort direction.
     */
    private Sort.Direction sortDirection(SupplierPage supplierPage) {
        return supplierPage.getSortDirection() != null ? supplierPage.getSortDirection() : Sort.Direction.ASC;
    }

    /**
     * Methods below determine the sorting options.
     *
     * @param supplierPage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    private String sortBy(SupplierPage supplierPage) {
        return supplierPage.getSortBy() != null ? supplierPage.getSortBy() : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(SupplierPage supplierPage) {
        return supplierPage.getPageSize() != null ? supplierPage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(SupplierPage supplierPage) {
        return supplierPage.getPageNumber() != null ? supplierPage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }
}
