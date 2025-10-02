/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

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
public class AssetSearchDao {
    private final EntityManager entityManager;
    private final CriteriaBuilder criteriaBuilder;

    /**
     * Constructor initializes the AssetSearchDao with an EntityManager.
     *
     * @param entityManager the entity manager used to access the database.
     */
    public AssetSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
    }


    /**
     * Finds all assets with the specified filters and pagination settings.
     *
     * @param assetPage           an object containing pagination and sorting information.
     * @param assetSearchCriteria an object containing search criteria to filter assets.
     * @return a paginated list of assets that match the specified search criteria.
     */
    public Page<Asset> findAllWithFilters(
            AssetPage assetPage,
            AssetSearchCriteria assetSearchCriteria
    ) {
        CriteriaQuery<Asset> criteriaQuery = criteriaBuilder.createQuery(Asset.class);
        Root<Asset> assetRoot = criteriaQuery.from(Asset.class);

        Predicate predicate = getPredicate(assetSearchCriteria, assetRoot);
        criteriaQuery.where(predicate);

        setOrder(assetPage, criteriaQuery, assetRoot);

        TypedQuery<Asset> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(assetPage) * pageSize(assetPage));
        typedQuery.setMaxResults(pageSize(assetPage));

        Pageable pageable = getPageable(assetPage);
        long assetsCount = getAssetsCountMethod(assetSearchCriteria);
        return new PageImpl<>(typedQuery.getResultList(), pageable, assetsCount);
    }


    /**
     * Builds a predicate for filtering assets based on search criteria.
     *
     * @param assetSearchCriteria the criteria to filter assets.
     * @param assetRoot           the root entity in the query.
     * @return a predicate containing the filtering conditions.
     */
    private Predicate getPredicate(AssetSearchCriteria assetSearchCriteria, Root<Asset> assetRoot) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(assetSearchCriteria.getAssetName())) {
            predicates.add(criteriaBuilder.like(assetRoot.get("assetName"), "%" + assetSearchCriteria.getAssetName() + "%"));
        }

        if (Objects.nonNull(assetSearchCriteria.getEngravedNumber())) {
            predicates.add(criteriaBuilder.like(assetRoot.get("engravedNumber"), "%" + assetSearchCriteria.getEngravedNumber() + "%"));
        }

        if (Objects.nonNull(assetSearchCriteria.getAssetTypeId())) {
            predicates.add(
                    criteriaBuilder.equal(assetRoot.get("assetType").get("id"), assetSearchCriteria.getAssetTypeId())
            );
        }

        if (Objects.nonNull(assetSearchCriteria.getAssetStatusId())) {
            predicates.add(
                    criteriaBuilder.equal(assetRoot.get("assetStatus").get("id"), assetSearchCriteria.getAssetStatusId())
            );
        }

        if (Objects.nonNull(assetSearchCriteria.getCommodityId())) {
            predicates.add(
                    criteriaBuilder.equal(assetRoot.get("commodity").get("id"), assetSearchCriteria.getCommodityId())
            );
        }

        if (Objects.nonNull(assetSearchCriteria.getAssignedToId())) {
            predicates.add(
                    criteriaBuilder.equal(assetRoot.get("assignedTo").get("id"), assetSearchCriteria.getAssignedToId())
            );
        }

        if (Objects.nonNull(assetSearchCriteria.getDisposed())) {
            predicates.add(criteriaBuilder.equal(assetRoot.get("disposed"), assetSearchCriteria.getDisposed()));
        }

        if (Objects.nonNull(assetSearchCriteria.getModel())) {
            predicates.add(criteriaBuilder.like(assetRoot.get("model"), "%" + assetSearchCriteria.getModel() + "%"));
        }

        if (Objects.nonNull(assetSearchCriteria.getStartDate())) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(assetRoot.get("createDate"), assetSearchCriteria.getStartDate()));
        }

        if (Objects.nonNull(assetSearchCriteria.getEndDate())) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(assetRoot.get("createDate"), assetSearchCriteria.getEndDate()));
        }

        if (Objects.nonNull(assetSearchCriteria.getAssignedTo())) {
            String searchName = "%" + assetSearchCriteria.getAssignedTo().toLowerCase() + "%";

            Predicate firstNameMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(assetRoot.get("assignedTo").get("firstName")), searchName
            );

            Predicate lastNameMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(assetRoot.get("assignedTo").get("lastName")), searchName
            );

            Predicate otherNameMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(assetRoot.get("assignedTo").get("otherName")), searchName
            );

            predicates.add(criteriaBuilder.or(firstNameMatch, lastNameMatch, otherNameMatch));
        }

        if (Objects.nonNull(assetSearchCriteria.getLocation())) {
            String locationSearch = "%" + assetSearchCriteria.getLocation().toLowerCase() + "%";

            Expression<String> assetBranchName = criteriaBuilder.lower(assetRoot.get("branch").get("name"));
            Expression<String> assignedBranchName = criteriaBuilder.lower(assetRoot.get("assignedTo").get("branch").get("name"));
            Expression<String> departmentName = criteriaBuilder.lower(assetRoot.get("assignedTo").get("department").get("name"));

            Predicate headOfficeByAssignedUser = criteriaBuilder.and(
                    criteriaBuilder.isNotNull(assetRoot.get("assignedTo")),
                    criteriaBuilder.equal(assignedBranchName, "head office"),
                    criteriaBuilder.like(criteriaBuilder.literal("head office"), locationSearch)
            );

            Predicate headOfficeUnassigned = criteriaBuilder.and(
                    criteriaBuilder.isNull(assetRoot.get("assignedTo")),
                    criteriaBuilder.like(assetBranchName, locationSearch),
                    criteriaBuilder.equal(assetBranchName, "head office")
            );

            Predicate matchDepartmentInHeadOffice = criteriaBuilder.and(
                    criteriaBuilder.isNotNull(assetRoot.get("assignedTo")),
                    criteriaBuilder.equal(assignedBranchName, "head office"),
                    criteriaBuilder.like(departmentName, locationSearch)
            );

            Predicate matchAssignedUserBranch = criteriaBuilder.and(
                    criteriaBuilder.isNotNull(assetRoot.get("assignedTo")),
                    criteriaBuilder.like(assignedBranchName, locationSearch)
            );

            Predicate matchAssetBranch = criteriaBuilder.like(assetBranchName, locationSearch);

            // Combine all possible cases
            predicates.add(criteriaBuilder.or(
                    matchDepartmentInHeadOffice,  // Match department if in head office
                    headOfficeByAssignedUser,     // Match if assigned user is in head office AND location is head office
                    headOfficeUnassigned,         // Match if unassigned but asset branch is head office
                    matchAssignedUserBranch,      // Match other branches
                    matchAssetBranch              // Match via asset branch
            ));
        }


        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Sets the sorting order for the query based on the properties in AssetPage.
     *
     * @param assetPage     contains sorting information.34
     * @param criteriaQuery the criteria query to apply the order.
     * @param assetRoot     the root entity in the criteria query.
     */
    private void setOrder(AssetPage assetPage, CriteriaQuery<Asset> criteriaQuery, Root<Asset> assetRoot) {
        if (sortDirection(assetPage).equals(Sort.Direction.ASC)) {
            criteriaQuery.orderBy(criteriaBuilder.asc(assetRoot.get(sortBy(assetPage))));
        } else {
            criteriaQuery.orderBy(criteriaBuilder.desc(assetRoot.get(sortBy(assetPage))));
        }
    }


    /**
     * Creates pagination settings based on AssetPage properties.
     *
     * @param assetPage contains pagination information such as page number and size.
     * @return a Pageable instance with pagination and optional sorting settings.
     */
    private Pageable getPageable(AssetPage assetPage) {
        Sort sort = Sort.by(sortDirection(assetPage), sortBy(assetPage));
        return PageRequest.of(pageNumber(assetPage), pageSize(assetPage), sort);
    }


    /**
     * Counts the total number of assets that match the search criteria.
     *
     * @param assetSearchCriteria the criteria to filter assets.
     * @return the total count of assets matching the criteria.
     */
    private long getAssetsCountMethod(AssetSearchCriteria assetSearchCriteria) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Asset> countRoot = countQuery.from(Asset.class);

        Predicate countPredicate = getPredicate(assetSearchCriteria, countRoot);
        countQuery.select(criteriaBuilder.count(countRoot)).where(countPredicate);

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Determines the sort direction.
     *
     * @param assetPage the criteria to determine the sort direction.
     * @return the sort direction.
     */
    private Sort.Direction sortDirection(AssetPage assetPage) {
        return assetPage.getSortDirection() != null ? assetPage.getSortDirection() : Sort.Direction.ASC;
    }


    /**
     * Methods below determine the sorting options.
     *
     * @param assetPage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    private String sortBy(AssetPage assetPage) {
        return assetPage.getSortBy() != null ? assetPage.getSortBy() : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(AssetPage assetPage) {
        return assetPage.getPageSize() != null ? assetPage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(AssetPage assetPage) {
        return assetPage.getPageNumber() != null ? assetPage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }

}
