/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

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
public class UserSearchDao {
    private final EntityManager entityManager;
    private final CriteriaBuilder criteriaBuilder;

    /**
     * Constructor initializes the UserSearchDao with an EntityManager.
     *
     * @param entityManager the entity manager used to access the database.
     */
    public UserSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
    }

    /**
     * Finds all users with the specified filters and pagination settings.
     *
     * @param userPage           an object containing pagination and sorting information.
     * @param userSearchCriteria an object containing search criteria to filter users.
     * @return a paginated list of users that match the specified search criteria.
     */
    public Page<User> findAllWithFilters(
            UserPage userPage,
            UserSearchCriteria userSearchCriteria
    ) {
        CriteriaQuery<User> criteriaQuery = criteriaBuilder.createQuery(User.class);
        Root<User> userRoot = criteriaQuery.from(User.class);

        Predicate predicate = getPredicate(userSearchCriteria, userRoot);
        criteriaQuery.where(predicate);

        setOrder(userPage, criteriaQuery, userRoot);

        TypedQuery<User> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(userPage) * pageSize(userPage));
        typedQuery.setMaxResults(pageSize(userPage));

        Pageable pageable = getPageable(userPage);
        long usersCount = getUsersCountMethod(userSearchCriteria);
        return new PageImpl<>(typedQuery.getResultList(), pageable, usersCount);
    }

    /**
     * Builds a predicate for filtering users based on search criteria.
     *
     * @param userSearchCriteria the criteria to filter users.
     * @param userRoot           the root entity in the query.
     * @return a predicate containing the filtering conditions.
     */
    private Predicate getPredicate(UserSearchCriteria userSearchCriteria, Root<User> userRoot) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(userSearchCriteria.getFirstName())) {
            predicates.add(criteriaBuilder.like(userRoot.get("first_name"), "%" + userSearchCriteria.getFirstName() + "%"));
        }

        if (Objects.nonNull(userSearchCriteria.getLastName())) {
            predicates.add(criteriaBuilder.like(userRoot.get("last_name"), "%" + userSearchCriteria.getLastName() + "%"));
        }

        if (Objects.nonNull(userSearchCriteria.getEmail())) {
            predicates.add(criteriaBuilder.like(userRoot.get("email"), "%" + userSearchCriteria.getEmail() + "%"));
        }

        if (Objects.nonNull(userSearchCriteria.getPhone())) {
            predicates.add(criteriaBuilder.like(userRoot.get("phone"), "%" + userSearchCriteria.getPhone() + "%"));
        }

        if (Objects.nonNull(userSearchCriteria.getAddress())) {
            predicates.add(criteriaBuilder.like(userRoot.get("address"), "%" + userSearchCriteria.getAddress() + "%"));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Sets the sorting order for the query based on the properties in UserPage.
     *
     * @param userPage      contains sorting information.34
     * @param criteriaQuery the criteria query to apply the order.
     * @param userRoot      the root entity in the criteria query.
     */
    private void setOrder(UserPage userPage, CriteriaQuery<User> criteriaQuery, Root<User> userRoot) {
        Sort.Direction sortDirection = sortDirection(userPage) != null ? sortDirection(userPage)
                : Sort.Direction.ASC;
        if (sortDirection.equals(Sort.Direction.ASC)) {
            criteriaQuery.orderBy(criteriaBuilder.asc(userRoot.get(sortBy(userPage))));
        } else {
            criteriaQuery.orderBy(criteriaBuilder.desc(userRoot.get(sortBy(userPage))));
        }
    }

    /**
     * Creates pagination settings based on UserPage properties.
     *
     * @param userPage contains pagination information such as page number and size.
     * @return a Pageable instance with pagination and optional sorting settings.
     */
    private Pageable getPageable(UserPage userPage) {
        Sort sort = Sort.by(userPage.getSortDirection(), sortBy(userPage));
        return PageRequest.of(pageNumber(userPage), pageSize(userPage), sort);
    }

    /**
     * Counts the total number of users that match the search criteria.
     *
     * @param userSearchCriteria the criteria to filter users.
     * @return the total count of users matching the criteria.
     */
    private long getUsersCountMethod(UserSearchCriteria userSearchCriteria) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<User> countRoot = countQuery.from(User.class);

        Predicate countPredicate = getPredicate(userSearchCriteria, countRoot);
        countQuery.select(criteriaBuilder.count(countRoot)).where(countPredicate);

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Determines the sort direction.
     *
     * @param userPage the criteria to determine the sort direction.
     * @return the sort direction.
     */
    private Sort.Direction sortDirection(UserPage userPage) {
        return userPage.getSortDirection() != null ? userPage.getSortDirection() : Sort.Direction.ASC;
    }

    /**
     * Methods below determine the sorting options.
     *
     * @param userPage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    private String sortBy(UserPage userPage) {
        return userPage.getSortBy() != null ? userPage.getSortBy() : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(UserPage userPage) {
        return userPage.getPageSize() != null ? userPage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(UserPage userPage) {
        return userPage.getPageNumber() != null ? userPage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }
}
