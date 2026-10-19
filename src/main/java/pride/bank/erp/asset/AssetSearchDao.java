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
            predicates.add(locationPredicate(assetRoot, assetSearchCriteria.getLocation()));
        }

        /*
         * The holder's department — an asset has none of its own.
         *
         * Through the cached LEFT joins rather than a dotted path: `assignedTo` is nullable, and
         * `assetRoot.get("assignedTo").get("department")` would compile to INNER joins that drop
         * every unassigned asset in the FROM clause. That is trap #1, and here it would not even be
         * visible - an unassigned asset has no department either way, so the result would look right
         * while the query was wrong for the next person to reuse the join.
         */
        if (Objects.nonNull(assetSearchCriteria.getDepartmentId())) {
            predicates.add(criteriaBuilder.equal(
                    holderDepartment(assetRoot).get("id"), assetSearchCriteria.getDepartmentId()));
        }

        if (Objects.nonNull(assetSearchCriteria.getBranchId())) {
            Long chosen = assetSearchCriteria.getBranchId();
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.equal(assetBranch(assetRoot).get("id"), chosen),
                    criteriaBuilder.equal(holderBranch(assetRoot).get("id"), chosen)
            ));
        }

        if (Objects.nonNull(assetSearchCriteria.getDisposedFrom())) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    assetRoot.get("disposalDate"), assetSearchCriteria.getDisposedFrom()));
        }

        if (Objects.nonNull(assetSearchCriteria.getDisposedTo())) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    assetRoot.get("disposalDate"), assetSearchCriteria.getDisposedTo()));
        }

        if (Objects.nonNull(assetSearchCriteria.getScopedBranchId())) {
            // Matches the asset's own branch or its holder's, so an item assigned to someone at this
            // branch stays visible even if the asset row still points elsewhere.
            Long scoped = assetSearchCriteria.getScopedBranchId();

            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.equal(assetBranch(assetRoot).get("id"), scoped),
                    criteriaBuilder.equal(holderBranch(assetRoot).get("id"), scoped)
            ));
        }

        /*
         * Soft-deleted assets, excluded from every listing and every export.
         *
         * Applied unconditionally rather than as an optional filter: a deleted record is not a
         * narrower view of the register, it is out of it. `includeDeleted` on the criteria is the
         * one way back in, and only the restore listing sets it.
         *
         * `isFalse` rather than `equal(..., false)` so the predicate reads the same way as the
         * column's NOT NULL DEFAULT FALSE, and a row written before the column existed still matches.
         */
        if (!Boolean.TRUE.equals(assetSearchCriteria.getIncludeDeleted())) {
            predicates.add(criteriaBuilder.isFalse(assetRoot.get("deleted")));
        } else {
            // The restore view: deleted assets only, not everything.
            predicates.add(criteriaBuilder.isTrue(assetRoot.get("deleted")));
        }

        if (Objects.nonNull(assetSearchCriteria.getScopedOwnerId())) {
            // SELF scope. Strict equality on the holder — an officer's listing is what is in their
            // hands, so an unassigned asset at their branch is not theirs to see here.
            predicates.add(criteriaBuilder.equal(
                    holderJoin(assetRoot).get("id"), assetSearchCriteria.getScopedOwnerId()));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * "Where is this asset" — its own branch, its holder's branch, or its holder's department.
     *
     * <p>Every association is reached through an explicit {@code LEFT} join. Path navigation
     * ({@code assetRoot.get("assignedTo").get("branch")}) builds an <b>INNER</b> join instead, which
     * filters in the FROM clause before any of these OR branches is evaluated. That is what this
     * predicate used to do, and the effect was severe: a Location filter could only ever return
     * assets assigned to a user who had <i>both</i> a branch <i>and</i> a department, so every asset
     * sitting in a store vanished — despite two of the branches below existing specifically to
     * include them.
     *
     * <p>The joins are reused rather than re-created because {@code Root#join} adds a new join each
     * time it is called; calling it per branch would multiply the FROM clause and, with it, the row
     * count.
     */
    private Predicate locationPredicate(Root<Asset> assetRoot, String location) {
        String search = "%" + location.toLowerCase() + "%";

        Join<Object, Object> holder = holderJoin(assetRoot);
        Expression<String> assetBranchName = criteriaBuilder.lower(assetBranch(assetRoot).get("name"));
        Expression<String> holderBranchName = criteriaBuilder.lower(holderBranch(assetRoot).get("name"));
        Expression<String> holderDepartmentName =
                criteriaBuilder.lower(holderDepartment(assetRoot).get("name"));

        return criteriaBuilder.or(
                // The asset's own branch.
                criteriaBuilder.like(assetBranchName, search),
                // Its holder's branch — an asset follows the person holding it.
                criteriaBuilder.like(holderBranchName, search),
                /*
                 * At Head Office people search by department, because one branch name covers
                 * eighteen people across four of them.
                 *
                 * Keyed on the branch's own flag rather than on `name = "head office"`, which is what
                 * stood here. A branch is renamed from Settings, and the day somebody makes it
                 * "Head Office - Kampala" this clause matches nothing and the department search
                 * silently stops working — with an empty result that reads as "no such assets".
                 * The same fault the Stock report had when it matched statuses by display name.
                 */
                criteriaBuilder.and(
                        criteriaBuilder.isTrue(holderBranch(assetRoot).get("isHeadOffice")),
                        criteriaBuilder.like(holderDepartmentName, search)
                )
        );
    }

    /*
     * Join helpers.
     *
     * Root#join creates a fresh join on every call, so asking for the same association twice widens
     * the FROM clause and can multiply rows. These return the existing join when there is one.
     */

    private Join<Object, Object> holderJoin(Root<Asset> assetRoot) {
        return reuseJoin(assetRoot, "assignedTo");
    }

    private Join<Object, Object> holderBranch(Root<Asset> assetRoot) {
        return reuseJoin(holderJoin(assetRoot), "branch");
    }

    private Join<Object, Object> assetBranch(Root<Asset> assetRoot) {
        return reuseJoin(assetRoot, "branch");
    }

    /** The holder's department. Shared with {@code locationPredicate}, so the joins stay flat. */
    private Join<Object, Object> holderDepartment(Root<Asset> assetRoot) {
        return reuseJoin(holderJoin(assetRoot), "department");
    }

    /**
     * The existing LEFT join for an attribute, or a new one.
     *
     * <p>{@code From#join} adds a join every time it is called, so asking for {@code assignedTo}
     * from two different predicates would put it in the FROM clause twice and multiply the rows —
     * and with them the count. Looking for an existing join first keeps the query flat however many
     * predicates need the same association.
     */
    @SuppressWarnings("unchecked")
    private Join<Object, Object> reuseJoin(From<?, ?> from, String attribute) {
        return (Join<Object, Object>) from.getJoins().stream()
                .filter(j -> attribute.equals(j.getAttribute().getName()))
                .findFirst()
                .orElseGet(() -> (Join) from.join(attribute, JoinType.LEFT));
    }

    /**
     * Sets the sorting order for the query based on the properties in AssetPage.
     *
     * @param assetPage     contains sorting information.34
     * @param criteriaQuery the criteria query to apply the order.
     * @param assetRoot     the root entity in the criteria query.
     */
    private void setOrder(AssetPage assetPage, CriteriaQuery<Asset> criteriaQuery, Root<Asset> assetRoot) {
        String field = sortBy(assetPage);
        boolean ascending = sortDirection(assetPage).equals(Sort.Direction.ASC);

        Order primary = ascending
                ? criteriaBuilder.asc(assetRoot.get(field))
                : criteriaBuilder.desc(assetRoot.get(field));

        /*
         * A tie-break on the primary key, always.
         *
         * Ordering on a single non-unique column leaves rows tied, and MySQL is free to return tied
         * rows in a different order on each query. With OFFSET/LIMIT paging that means the same asset
         * can appear on page one and again on page two while another appears on neither — the table
         * looks like it is losing records. Sorting by asset name, model or status made this routine;
         * only `id` happened to be safe, because it is unique.
         *
         * Appending id makes the total order deterministic, so a page boundary always falls in the
         * same place.
         */
        if (DEFAULT_SORT_FIELD.equals(field)) {
            criteriaQuery.orderBy(primary);
            return;
        }
        Order tieBreak = ascending
                ? criteriaBuilder.asc(assetRoot.get(DEFAULT_SORT_FIELD))
                : criteriaBuilder.desc(assetRoot.get(DEFAULT_SORT_FIELD));
        criteriaQuery.orderBy(primary, tieBreak);
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
    /**
     * Newest-first when nothing is asked for.
     *
     * <p>Matches the controller's own default. This used to fall back to ASC, so the DAO and the
     * endpoint in front of it disagreed on what "unspecified" meant — harmless only because the
     * controller always sends a value, and a trap for any caller that did not.
     */
    private Sort.Direction sortDirection(AssetPage assetPage) {
        return assetPage.getSortDirection() != null ? assetPage.getSortDirection() : Sort.Direction.DESC;
    }


    /**
     * Methods below determine the sorting options.
     *
     * @param assetPage the criteria to determine the sorting criteria.
     * @return the field to sort, page size or page number of the record.
     */
    /**
     * Columns a caller may sort on.
     *
     * <p>Whitelisted because the value goes straight into {@code assetRoot.get(...)}: anything that
     * is not a mapped attribute throws {@link IllegalArgumentException} and the request becomes a
     * 500. Associations are excluded too — sorting by one would need a join this method cannot add.
     */
    private static final java.util.Set<String> SORTABLE = java.util.Set.of(
            "id", "assetName", "engravedNumber", "serialNumber", "tagName", "make", "model",
            "hostname", "dateReceipt", "purchaseCost", "costOfTheAsset", "createDate", "lastModified"
    );

    private String sortBy(AssetPage assetPage) {
        String requested = assetPage.getSortBy();
        // An unrecognised column falls back rather than failing: a stale bookmark or a renamed field
        // should still show the register, in a defined order, instead of an error page.
        return (requested != null && SORTABLE.contains(requested)) ? requested : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(AssetPage assetPage) {
        return assetPage.getPageSize() != null ? assetPage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(AssetPage assetPage) {
        return assetPage.getPageNumber() != null ? assetPage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }

}
