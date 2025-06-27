package pride.bank.erp.assetAssignmentHostory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetPage;
import pride.bank.erp.asset.AssetSearchCriteria;

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
    private long getAssetAssignmentHistoryCountMethod(AssetSearchCriteria assetSearchCriteria) {
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
