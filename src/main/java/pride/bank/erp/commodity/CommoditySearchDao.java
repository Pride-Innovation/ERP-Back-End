/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.commodity;

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
public class CommoditySearchDao {
    private final EntityManager entityManager;
    private final CriteriaBuilder criteriaBuilder;

    /**
     * Constructor initializes the CommoditySearchDao with an EntityManager.
     *
     * @param entityManager the entity manager used to access the database.
     */
    public CommoditySearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
    }

    /**
     * Finds all commodities with the specified filters and pagination settings.
     *
     * @param commodityPage           an object containing pagination and sorting information.
     * @param commoditySearchCriteria an object containing search criteria to filter commodities.
     * @return a paginated list of commodities that match the specified search criteria.
     */
    public Page<Commodity> findAllWithFilters(
            CommodityPage commodityPage,
            CommoditySearchCriteria commoditySearchCriteria
    ) {
        CriteriaQuery<Commodity> criteriaQuery = criteriaBuilder.createQuery(Commodity.class);
        Root<Commodity> commodityRoot = criteriaQuery.from(Commodity.class);

        Predicate predicate = getPredicate(commoditySearchCriteria, commodityRoot);
        criteriaQuery.where(predicate);

        setOrder(commodityPage, criteriaQuery, commodityRoot);

        TypedQuery<Commodity> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(commodityPage) * pageSize(commodityPage));
        typedQuery.setMaxResults(pageSize(commodityPage));

        Pageable pageable = getPageable(commodityPage);
        long commoditiesCount = getCommoditiesCountMethod(commoditySearchCriteria);
        return new PageImpl<>(typedQuery.getResultList(), pageable, commoditiesCount);
    }

    /**
     * Builds a predicate for filtering commodities based on search criteria.
     *
     * @param commoditySearchCriteria the criteria to filter commodities.
     * @param commodityRoot           the root entity in the query.
     * @return a predicate containing the filtering conditions.
     */
    private Predicate getPredicate(CommoditySearchCriteria commoditySearchCriteria, Root<Commodity> commodityRoot) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(commoditySearchCriteria.getName())) {
            predicates.add(criteriaBuilder.like(commodityRoot.get("name"), "%" + commoditySearchCriteria.getName() + "%"));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Sets the sorting order for the query based on the properties in CommodityPage.
     *
     * @param commodityPage contains sorting information.34
     * @param criteriaQuery the criteria query to apply the order.
     * @param commodityRoot the root entity in the criteria query.
     */
    private void setOrder(
            CommodityPage commodityPage,
            CriteriaQuery<Commodity> criteriaQuery,
            Root<Commodity> commodityRoot) {
        Sort.Direction sortDirection = sortDirection(commodityPage) != null ? sortDirection(commodityPage)
                : Sort.Direction.ASC;
        if (sortDirection.equals(Sort.Direction.ASC)) {
            criteriaQuery.orderBy(criteriaBuilder.asc(commodityRoot.get(sortBy(commodityPage))));
        } else {
            criteriaQuery.orderBy(criteriaBuilder.desc(commodityRoot.get(sortBy(commodityPage))));
        }
    }

    /**
     * Creates pagination settings based on CommodityPage properties.
     *
     * @param commodityPage contains pagination information such as page number and size.
     * @return a Pageable instance with pagination and optional sorting settings.
     */
    private Pageable getPageable(CommodityPage commodityPage) {
        Sort sort = Sort.by(commodityPage.getSortDirection(), sortBy(commodityPage));
        return PageRequest.of(pageNumber(commodityPage), pageSize(commodityPage), sort);
    }

    /**
     * Counts the total number of commodities that match the search criteria.
     *
     * @param commoditySearchCriteria the criteria to filter commodities.
     * @return the total count of commodities matching the criteria.
     */
    private long getCommoditiesCountMethod(CommoditySearchCriteria commoditySearchCriteria) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Commodity> countRoot = countQuery.from(Commodity.class);

        Predicate countPredicate = getPredicate(commoditySearchCriteria, countRoot);
        countQuery.select(criteriaBuilder.count(countRoot)).where(countPredicate);

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Determines the sort direction.
     *
     * @param commodityPage the criteria to determine the sort direction.
     * @return the sort direction.
     */
    private Sort.Direction sortDirection(CommodityPage commodityPage) {
        return commodityPage.getSortDirection() != null ? commodityPage.getSortDirection() : Sort.Direction.ASC;
    }

    /**
     * Methods below determine the sorting options.
     *
     * @param commodityPage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    private String sortBy(CommodityPage commodityPage) {
        return commodityPage.getSortBy() != null ? commodityPage.getSortBy() : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(CommodityPage commodityPage) {
        return commodityPage.getPageSize() != null ? commodityPage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(CommodityPage commodityPage) {
        return commodityPage.getPageNumber() != null ? commodityPage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }
}
