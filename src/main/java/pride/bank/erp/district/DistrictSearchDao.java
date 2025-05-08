/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.district;

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
public class DistrictSearchDao {
    private final EntityManager entityManager;
    private final CriteriaBuilder criteriaBuilder;

    /**
     * Constructor initializes the DistrictSearchDao with an EntityManager.
     *
     * @param entityManager the entity manager used to access the database.
     */
    public DistrictSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
    }

    /**
     * Finds all districts with the specified filters and pagination settings.
     *
     * @param districtPage           an object containing pagination and sorting information.
     * @param districtSearchCriteria an object containing search criteria to filter districts.
     * @return a paginated list of districts that match the specified search criteria.
     */
    public Page<District> findAllWithFilters(
            DistrictPage districtPage,
            DistrictSearchCriteria districtSearchCriteria
    ) {
        CriteriaQuery<District> criteriaQuery = criteriaBuilder.createQuery(District.class);
        Root<District> districtRoot = criteriaQuery.from(District.class);

        Predicate predicate = getPredicate(districtSearchCriteria, districtRoot);
        criteriaQuery.where(predicate);

        setOrder(districtPage, criteriaQuery, districtRoot);

        TypedQuery<District> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(districtPage) * pageSize(districtPage));
        typedQuery.setMaxResults(pageSize(districtPage));

        Pageable pageable = getPageable(districtPage);
        long districtsCount = getDistrictsCountMethod(districtSearchCriteria);
        return new PageImpl<>(typedQuery.getResultList(), pageable, districtsCount);
    }

    /**
     * Builds a predicate for filtering districts based on search criteria.
     *
     * @param districtSearchCriteria the criteria to filter districts.
     * @param districtRoot           the root entity in the query.
     * @return a predicate containing the filtering conditions.
     */
    private Predicate getPredicate(DistrictSearchCriteria districtSearchCriteria, Root<District> districtRoot) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(districtSearchCriteria.getName())) {
            predicates.add(criteriaBuilder.like(districtRoot.get("name"), "%" + districtSearchCriteria.getName() + "%"));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Sets the sorting order for the query based on the properties in DistrictPage.
     *
     * @param districtPage  contains sorting information.34
     * @param criteriaQuery the criteria query to apply the order.
     * @param districtRoot  the root entity in the criteria query.
     */
    private void setOrder(
            DistrictPage districtPage,
            CriteriaQuery<District> criteriaQuery,
            Root<District> districtRoot) {
        Sort.Direction sortDirection = sortDirection(districtPage) != null ? sortDirection(districtPage)
                : Sort.Direction.ASC;
        if (sortDirection.equals(Sort.Direction.ASC)) {
            criteriaQuery.orderBy(criteriaBuilder.asc(districtRoot.get(sortBy(districtPage))));
        } else {
            criteriaQuery.orderBy(criteriaBuilder.desc(districtRoot.get(sortBy(districtPage))));
        }
    }

    /**
     * Creates pagination settings based on DistrictPage properties.
     *
     * @param districtPage contains pagination information such as page number and size.
     * @return a Pageable instance with pagination and optional sorting settings.
     */
    private Pageable getPageable(DistrictPage districtPage) {
        Sort sort = Sort.by(districtPage.getSortDirection(), sortBy(districtPage));
        return PageRequest.of(pageNumber(districtPage), pageSize(districtPage), sort);
    }

    /**
     * Counts the total number of districts that match the search criteria.
     *
     * @param districtSearchCriteria the criteria to filter districts.
     * @return the total count of Districts matching the criteria.
     */
    private long getDistrictsCountMethod(DistrictSearchCriteria districtSearchCriteria) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<District> countRoot = countQuery.from(District.class);

        Predicate countPredicate = getPredicate(districtSearchCriteria, countRoot);
        countQuery.select(criteriaBuilder.count(countRoot)).where(countPredicate);

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Determines the sort direction.
     *
     * @param districtPage the criteria to determine the sort direction.
     * @return the sort direction.
     */
    private Sort.Direction sortDirection(DistrictPage districtPage) {
        return districtPage.getSortDirection() != null ? districtPage.getSortDirection() : Sort.Direction.ASC;
    }

    /**
     * Methods below determine the sorting options.
     *
     * @param districtPage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    private String sortBy(DistrictPage districtPage) {
        return districtPage.getSortBy() != null ? districtPage.getSortBy() : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(DistrictPage districtPage) {
        return districtPage.getPageSize() != null ? districtPage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(DistrictPage districtPage) {
        return districtPage.getPageNumber() != null ? districtPage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }
}
