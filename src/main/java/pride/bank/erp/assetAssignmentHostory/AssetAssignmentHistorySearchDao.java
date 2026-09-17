/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetAssignmentHostory;

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
public class AssetAssignmentHistorySearchDao {
    private final EntityManager entityManager;
    private final CriteriaBuilder criteriaBuilder;

    /**
     * Constructor initializes the AssetAssignmentHistorySearchDao with an EntityManager.
     *
     * @param entityManager the entity manager used to access the database.
     */
    public AssetAssignmentHistorySearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
    }


    /**
     * Finds all AssetAssignmentHistory with the specified filters and pagination settings.
     *
     * @param assetAssignmentHistoryPage           an object containing pagination and sorting information.
     * @param assetAssignmentHistorySearchCriteria an object containing search criteria to filter AssetAssignmentHistory.
     * @return a paginated list of assets that match the specified search criteria.
     */
    public Page<AssetAssignmentHistory> findAllWithFilters(
            AssetAssignmentHistoryPage assetAssignmentHistoryPage,
            AssetAssignmentHistorySearchCriteria assetAssignmentHistorySearchCriteria
    ) {
        CriteriaQuery<AssetAssignmentHistory> criteriaQuery = criteriaBuilder.createQuery(AssetAssignmentHistory.class);
        Root<AssetAssignmentHistory> assetAssignmentHistoryRoot = criteriaQuery.from(AssetAssignmentHistory.class);

        Predicate predicate = getPredicate(assetAssignmentHistorySearchCriteria, assetAssignmentHistoryRoot);
        criteriaQuery.where(predicate);

        setOrder(assetAssignmentHistoryPage, criteriaQuery, assetAssignmentHistoryRoot);

        TypedQuery<AssetAssignmentHistory> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(assetAssignmentHistoryPage) * pageSize(assetAssignmentHistoryPage));
        typedQuery.setMaxResults(pageSize(assetAssignmentHistoryPage));

        Pageable pageable = getPageable(assetAssignmentHistoryPage);
        long assetAssignmentHistoryCount = getAssetAssignmentHistoryCountMethod(assetAssignmentHistorySearchCriteria);
        return new PageImpl<>(typedQuery.getResultList(), pageable, assetAssignmentHistoryCount);
    }


    /**
     * Builds a predicate for filtering getAssetAssignmentHistoryCountMethod based on search criteria.
     *
     * @param assetAssignmentHistorySearchCriteria the criteria to filter AssetAssignmentHistory.
     * @param assetAssignmentHistoryRoot           the root entity in the query.
     * @return a predicate containing the filtering conditions.
     */
    private Predicate getPredicate(
            AssetAssignmentHistorySearchCriteria assetAssignmentHistorySearchCriteria,
            Root<AssetAssignmentHistory> assetAssignmentHistoryRoot
    ) {
        List<Predicate> predicates = new ArrayList<>();


        if (Objects.nonNull(assetAssignmentHistorySearchCriteria.getAssetId())) {
            predicates.add(
                    criteriaBuilder.equal(assetAssignmentHistoryRoot.get("asset").get("id"),
                            assetAssignmentHistorySearchCriteria.getAssetId())
            );
        }

        /*
         * Branch scope, via explicit LEFT JOINs.
         *
         * Written as `root.get("asset").get("branch").get("id")` this would compile to two INNER
         * joins sitting in the FROM clause, and they filter before the OR below is evaluated — so a
         * history row whose asset has no branch, or whose holder has none, would vanish from every
         * scoped listing rather than being matched by the other side of the OR. That is trap #1 in
         * CLAUDE.md and it has caused four separate bugs in this codebase already.
         *
         * Matches the asset's own branch or its holder's, the same pair AssetSearchDao uses, so an
         * item assigned to someone at this branch stays visible even when the asset row still points
         * elsewhere.
         */
        if (Objects.nonNull(assetAssignmentHistorySearchCriteria.getScopedBranchId())) {
            Long scoped = assetAssignmentHistorySearchCriteria.getScopedBranchId();

            jakarta.persistence.criteria.Join<Object, Object> asset =
                    assetAssignmentHistoryRoot.join("asset", jakarta.persistence.criteria.JoinType.LEFT);
            jakarta.persistence.criteria.Join<Object, Object> assetBranch =
                    asset.join("branch", jakarta.persistence.criteria.JoinType.LEFT);
            jakarta.persistence.criteria.Join<Object, Object> holderBranch =
                    assetAssignmentHistoryRoot
                            .join("user", jakarta.persistence.criteria.JoinType.LEFT)
                            .join("branch", jakarta.persistence.criteria.JoinType.LEFT);

            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.equal(assetBranch.get("id"), scoped),
                    criteriaBuilder.equal(holderBranch.get("id"), scoped)
            ));
        }

        // SELF scope: the trail of what this person has personally held.
        if (Objects.nonNull(assetAssignmentHistorySearchCriteria.getScopedOwnerId())) {
            predicates.add(criteriaBuilder.equal(
                    assetAssignmentHistoryRoot.join("user", jakarta.persistence.criteria.JoinType.LEFT).get("id"),
                    assetAssignmentHistorySearchCriteria.getScopedOwnerId()));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Sets the sorting order for the query based on the properties in AssetAssignmentHistoryPage.
     *
     * @param assetAssignmentHistoryPage contains sorting information.34
     * @param criteriaQuery              the criteria query to apply the order.
     * @param assetAssignmentHistoryRoot the root entity in the criteria query.
     */
    private void setOrder(AssetAssignmentHistoryPage assetAssignmentHistoryPage,
                          CriteriaQuery<AssetAssignmentHistory> criteriaQuery,
                          Root<AssetAssignmentHistory> assetAssignmentHistoryRoot) {
        if (sortDirection(assetAssignmentHistoryPage).equals(Sort.Direction.ASC)) {
            criteriaQuery.orderBy(criteriaBuilder.asc(assetAssignmentHistoryRoot
                    .get(sortBy(assetAssignmentHistoryPage))));
        } else {
            criteriaQuery.orderBy(criteriaBuilder.desc(assetAssignmentHistoryRoot
                    .get(sortBy(assetAssignmentHistoryPage))));
        }
    }


    /**
     * Creates pagination settings based on AssetAssignmentHistoryPage properties.
     *
     * @param assetAssignmentHistoryPage contains pagination information such as page number and size.
     * @return a Pageable instance with pagination and optional sorting settings.
     */
    private Pageable getPageable(AssetAssignmentHistoryPage assetAssignmentHistoryPage) {
        Sort sort = Sort.by(sortDirection(assetAssignmentHistoryPage), sortBy(assetAssignmentHistoryPage));
        return PageRequest.of(pageNumber(assetAssignmentHistoryPage), pageSize(assetAssignmentHistoryPage), sort);
    }


    /**
     * Counts the total number of AssetAssignmentHistory that match the search criteria.
     *
     * @param assetAssignmentHistorySearchCriteria the criteria to filter AssetAssignmentHistory.
     * @return the total count of AssetAssignmentHistory matching the criteria.
     */
    private long getAssetAssignmentHistoryCountMethod(
            AssetAssignmentHistorySearchCriteria assetAssignmentHistorySearchCriteria
    ) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<AssetAssignmentHistory> countRoot = countQuery.from(AssetAssignmentHistory.class);

        Predicate countPredicate = getPredicate(assetAssignmentHistorySearchCriteria, countRoot);
        countQuery.select(criteriaBuilder.count(countRoot)).where(countPredicate);

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Determines the sort direction.
     *
     * @param assetAssignmentHistoryPage the criteria to determine the sort direction.
     * @return the sort direction.
     */
    private Sort.Direction sortDirection(AssetAssignmentHistoryPage assetAssignmentHistoryPage) {
        return assetAssignmentHistoryPage.getSortDirection() != null ?
                assetAssignmentHistoryPage.getSortDirection() : Sort.Direction.ASC;
    }


    /**
     * Methods below determine the sorting options.
     *
     * @param assetAssignmentHistoryPage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    private String sortBy(AssetAssignmentHistoryPage assetAssignmentHistoryPage) {
        return assetAssignmentHistoryPage.getSortBy() != null ?
                assetAssignmentHistoryPage.getSortBy() : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(AssetAssignmentHistoryPage assetAssignmentHistoryPage) {
        return assetAssignmentHistoryPage.getPageSize() != null ?
                assetAssignmentHistoryPage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(AssetAssignmentHistoryPage assetAssignmentHistoryPage) {
        return assetAssignmentHistoryPage.getPageNumber() != null ?
                assetAssignmentHistoryPage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }
}
