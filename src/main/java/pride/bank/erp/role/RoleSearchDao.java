/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.role;

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
public class RoleSearchDao {

    private final EntityManager entityManager;
    private final CriteriaBuilder criteriaBuilder;

    /**
     * Constructor initializes the RoleSearchDao with an EntityManager.
     *
     * @param entityManager the entity manager used to access the database.
     */
    public RoleSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
    }

    /**
     * Finds all roles with the specified filters and pagination settings.
     *
     * @param rolePage           an object containing pagination and sorting information.
     * @param roleSearchCriteria an object containing search criteria to filter roles.
     * @return a paginated list of roles that match the specified search criteria.
     */
    public Page<Role> findAllWithFilters(
            RolePage rolePage,
            RoleSearchCriteria roleSearchCriteria
    ) {
        CriteriaQuery<Role> criteriaQuery = criteriaBuilder.createQuery(Role.class);
        Root<Role> roleRoot = criteriaQuery.from(Role.class);

        Predicate predicate = getPredicate(roleSearchCriteria, roleRoot);
        criteriaQuery.where(predicate);

        setOrder(rolePage, criteriaQuery, roleRoot);

        TypedQuery<Role> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(rolePage) * pageSize(rolePage));
        typedQuery.setMaxResults(pageSize(rolePage));

        Pageable pageable = getPageable(rolePage);
        long rolesCount = getRolesCountMethod(roleSearchCriteria);
        return new PageImpl<>(typedQuery.getResultList(), pageable, rolesCount);
    }

    /**
     * Builds a predicate for filtering roles based on search criteria.
     *
     * @param roleSearchCriteria the criteria to filter roles.
     * @param roleRoot           the root entity in the query.
     * @return a predicate containing the filtering conditions.
     */
    private Predicate getPredicate(RoleSearchCriteria roleSearchCriteria, Root<Role> roleRoot) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(roleSearchCriteria.getName())) {
            predicates.add(criteriaBuilder.like(roleRoot.get("name"), "%" + roleSearchCriteria.getName() + "%"));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Sets the sorting order for the query based on the properties in RolePage.
     *
     * @param rolePage      contains sorting information.34
     * @param criteriaQuery the criteria query to apply the order.
     * @param roleRoot      the root entity in the criteria query.
     */
    private void setOrder(RolePage rolePage, CriteriaQuery<Role> criteriaQuery, Root<Role> roleRoot) {
        if (sortDirection(rolePage).equals(Sort.Direction.ASC)) {
            criteriaQuery.orderBy(criteriaBuilder.asc(roleRoot.get(sortBy(rolePage))));
        } else {
            criteriaQuery.orderBy(criteriaBuilder.desc(roleRoot.get(sortBy(rolePage))));
        }
    }

    /**
     * Creates pagination settings based on RolePage properties.
     *
     * @param rolePage contains pagination information such as page number and size.
     * @return a Pageable instance with pagination and optional sorting settings.
     */
    private Pageable getPageable(RolePage rolePage) {
        Sort sort = Sort.by(sortDirection(rolePage), sortBy(rolePage));
        return PageRequest.of(pageNumber(rolePage), pageSize(rolePage), sort);
    }

    /**
     * Counts the total number of roles that match the search criteria.
     *
     * @param roleSearchCriteria the criteria to filter roles.
     * @return the total count of roles matching the criteria.
     */
    private long getRolesCountMethod(RoleSearchCriteria roleSearchCriteria) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Role> countRoot = countQuery.from(Role.class);

        Predicate countPredicate = getPredicate(roleSearchCriteria, countRoot);
        countQuery.select(criteriaBuilder.count(countRoot)).where(countPredicate);

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Determines the sort direction.
     *
     * @param rolePage the criteria to determine the sort direction.
     * @return the sort direction.
     */
    private Sort.Direction sortDirection(RolePage rolePage) {
        return rolePage.getSortDirection() != null ? rolePage.getSortDirection() : Sort.Direction.ASC;
    }

    /**
     * Methods below determine the sorting options.
     *
     * @param rolePage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    private String sortBy(RolePage rolePage) {
        return rolePage.getSortBy() != null ? rolePage.getSortBy() : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(RolePage rolePage) {
        return rolePage.getPageSize() != null ? rolePage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(RolePage rolePage) {
        return rolePage.getPageNumber() != null ? rolePage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }
}
