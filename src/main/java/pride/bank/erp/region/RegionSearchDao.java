/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.region;

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
public class RegionSearchDao {
    private final EntityManager entityManager;
    private final CriteriaBuilder criteriaBuilder;

    /**
     * Constructor initializes the RegionSearchDao with an EntityManager.
     *
     * @param entityManager the entity manager used to access the database.
     */
    public RegionSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
    }


    /**
     * Finds all regions with the specified filters and pagination settings.
     *
     * @param regionPage           an object containing pagination and sorting information.
     * @param regionSearchCriteria an object containing search criteria to filter requests.
     * @return a paginated list of regions that match the specified search criteria.
     */
    public Page<Region> findAllWithFilters(
            RegionPage regionPage,
            RegionSearchCriteria regionSearchCriteria
    ) {
        CriteriaQuery<Region> criteriaQuery = criteriaBuilder.createQuery(Region.class);
        Root<Region> regionRoot = criteriaQuery.from(Region.class);

        Predicate predicate = getPredicate(regionSearchCriteria, regionRoot);
        criteriaQuery.where(predicate);

        setOrder(regionPage, criteriaQuery, regionRoot);

        TypedQuery<Region> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(regionPage) * pageSize(regionPage));
        typedQuery.setMaxResults(pageSize(regionPage));

        Pageable pageable = getPageable(regionPage);
        long regionsCount = getRegionsCountMethod(regionSearchCriteria);
        return new PageImpl<>(typedQuery.getResultList(), pageable, regionsCount);
    }

    /**
     * Builds a predicate for filtering regions based on search criteria.
     *
     * @param regionSearchCriteria the criteria to filter regions.
     * @param regionRoot           the root entity in the query.
     * @return a predicate containing the filtering conditions.
     */
    private Predicate getPredicate(RegionSearchCriteria regionSearchCriteria, Root<Region> regionRoot) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(regionSearchCriteria.getName())) {
            predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(regionRoot.get("name")),
                    "%" + regionSearchCriteria.getName().toLowerCase() + "%"));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Sets the sorting order for the query based on the properties in RegionPage.
     *
     * @param regionPage    contains sorting information.34
     * @param criteriaQuery the criteria query to apply the order.
     * @param regionRoot    the root entity in the criteria query.
     */
    private void setOrder(RegionPage regionPage, CriteriaQuery<Region> criteriaQuery, Root<Region> regionRoot) {
        Sort.Direction sortDirection = sortDirection(regionPage) != null ? sortDirection(regionPage)
                : Sort.Direction.ASC;
        if (sortDirection.equals(Sort.Direction.ASC)) {
            criteriaQuery.orderBy(criteriaBuilder.asc(regionRoot.get(sortBy(regionPage))));
        } else {
            criteriaQuery.orderBy(criteriaBuilder.desc(regionRoot.get(sortBy(regionPage))));
        }
    }

    /**
     * Creates pagination settings based on RegionPage properties.
     *
     * @param regionPage contains pagination information such as page number and size.
     * @return a Pageable instance with pagination and optional sorting settings.
     */
    private Pageable getPageable(RegionPage regionPage) {
        Sort sort = Sort.by(regionPage.getSortDirection(), sortBy(regionPage));
        return PageRequest.of(pageNumber(regionPage), pageSize(regionPage), sort);
    }


    /**
     * Counts the total number of regions that match the search criteria.
     *
     * @param regionSearchCriteria the criteria to filter regions.
     * @return the total count of Regions matching the criteria.
     */
    private long getRegionsCountMethod(RegionSearchCriteria regionSearchCriteria) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Region> countRoot = countQuery.from(Region.class);

        Predicate countPredicate = getPredicate(regionSearchCriteria, countRoot);
        countQuery.select(criteriaBuilder.count(countRoot)).where(countPredicate);

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Determines the sort direction.
     *
     * @param regionPage the criteria to determine the sort direction.
     * @return the sort direction.
     */
    private Sort.Direction sortDirection(RegionPage regionPage) {
        return regionPage.getSortDirection() != null ? regionPage.getSortDirection() : Sort.Direction.ASC;
    }

    /**
     * Methods below determine the sorting options.
     *
     * @param regionPage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    private String sortBy(RegionPage regionPage) {
        return regionPage.getSortBy() != null ? regionPage.getSortBy() : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(RegionPage regionPage) {
        return regionPage.getPageSize() != null ? regionPage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(RegionPage regionPage) {
        return regionPage.getPageNumber() != null ? regionPage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }
}
