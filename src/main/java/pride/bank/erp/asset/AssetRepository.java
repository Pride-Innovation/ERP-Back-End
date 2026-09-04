/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    @Query(value = """
                SELECT at.name AS assetTypeName,
                       COUNT(a.id) AS totalCount,
                       MAX(a.create_date) AS lastUpdatedDate
                FROM asset a
                LEFT JOIN commodity c ON a.commodity_id = c.id
                LEFT JOIN asset_type at ON c.asset_type_id = at.id
                WHERE at.name IS NOT NULL
                  AND a.deleted = false
                GROUP BY at.name
            """, nativeQuery = true)
    List<AssetTypeSummaryView> getAssetTypeSummary();

    /**
     * The same totals as {@link #getAssetTypeSummary()}, but split by branch — one row per
     * branch/category pair that actually has assets.
     *
     * Resolves the category through {@code a.assetType} rather than through the commodity, so this
     * agrees with {@link #getAssetStatsByType(Long)} and the branch-scoped and national views on
     * the dashboard cannot disagree about which category an asset belongs to.
     *
     * LEFT JOIN on branch on purpose: an asset with no branch yet still counts towards its
     * category, and dropping it here would make the grid's totals fail to reconcile with the
     * category totals. Those rows come back with a null {@code branchName}.
     *
     * Like the query above, disposed assets are not excluded — both views count every record on
     * the register.
     */
    @Query("""
            SELECT b.name AS branchName,
                   at.name AS assetTypeName,
                   COUNT(a.id) AS totalCount
            FROM Asset a
            LEFT JOIN a.branch b
            JOIN a.assetType at
            WHERE at.name IS NOT NULL
              AND a.deleted = false
            GROUP BY b.name, at.name
            ORDER BY b.name, at.name
            """)
    List<AssetBranchSummaryView> getAssetSummaryByBranchAndType();

    @Query("""
            SELECT
                a.assetType.name AS assetType,
                COUNT(a.id) AS total,
                SUM(CASE WHEN a.assetStatus.status = 'inMaintenance' THEN 1 ELSE 0 END) AS inMaintenance,
                SUM(CASE WHEN a.assetStatus.status = 'requireUpdate' THEN 1 ELSE 0 END) AS unassigned,
                SUM(CASE WHEN a.assetStatus.status NOT IN ('inMaintenance', 'requireUpdate') THEN 1 ELSE 0 END) AS assigned
            FROM Asset a
            WHERE a.branch.id = :branchId
              AND a.deleted = false
            GROUP BY a.assetType.name
            ORDER BY a.assetType.name
            """)
    List<AssetTypeStatsProjection> getAssetStatsByType(@Param("branchId") Long branchId);
    /**
     * The same per-category totals, narrowed to whatever the viewer's dashboard scope allows.
     *
     * <p>Replaces {@link #getAssetStatsByType(Long)} for the dashboard, which took a branch id and
     * nothing else — so the figures were always one branch's, and a Head Office user's headline
     * numbers showed Head Office's own assets rather than the bank's, with nothing on screen saying
     * so. Both filters are null-tolerant, which lets one query serve all three scopes:
     *
     * <ul>
     *   <li>both null — every branch (the national roll-up, for DASH_SCOPE_ALL)</li>
     *   <li>branchId set — one branch (BRANCH, or ALL focused through the selector)</li>
     *   <li>ownerId set — only what that person holds (SELF)</li>
     * </ul>
     *
     * <p>The four state counts partition the total exactly — an asset is disposed, or it is one of
     * the three live states. The live buckets each carry an explicit {@code disposed <> true} guard
     * because {@code assigned} was previously defined as "not in-maintenance and not require-update",
     * which swept every written-off asset into the In Use figure.
     *
     * <p>{@code branch} and {@code assignedTo} are reached through explicit LEFT joins rather than
     * dotted paths. A dotted path builds an INNER join that sits in the FROM clause and filters
     * before the null check is ever evaluated, so the national roll-up would silently drop every
     * asset with no branch — and the SELF case every asset with no holder. That trap has already
     * cost this codebase a branch-scoped movement listing and an asset location filter.
     */
    @Query("""
            SELECT
                at.name AS assetType,
                COUNT(a.id) AS total,
                SUM(CASE WHEN a.disposed = true THEN 1 ELSE 0 END) AS disposed,
                SUM(CASE WHEN a.disposed <> true AND a.assetStatus.status = 'inMaintenance' THEN 1 ELSE 0 END) AS inMaintenance,
                SUM(CASE WHEN a.disposed <> true AND a.assetStatus.status = 'requireUpdate' THEN 1 ELSE 0 END) AS unassigned,
                SUM(CASE WHEN a.disposed <> true AND a.assetStatus.status NOT IN ('inMaintenance', 'requireUpdate') THEN 1 ELSE 0 END) AS assigned
            FROM Asset a
            JOIN a.assetType at
            LEFT JOIN a.branch b
            LEFT JOIN a.assignedTo holder
            WHERE (:branchId IS NULL OR b.id = :branchId)
              AND a.deleted = false
              AND (:ownerId IS NULL OR holder.id = :ownerId)
            GROUP BY at.name
            ORDER BY at.name
            """)
    List<AssetTypeStatsProjection> getAssetStatsScoped(
            @Param("branchId") Long branchId,
            @Param("ownerId") Long ownerId);


    @Query("""
                SELECT a
                FROM Asset a
                JOIN FETCH a.assetType at
                LEFT JOIN FETCH a.assetStatus s
                WHERE a.assignedTo.id = :userId
                AND a.deleted = false
            """)
    List<Asset> findAllByAssignedToIdWithTypeAndStatus(@Param("userId") Long userId);

    Optional<Asset> findByTagName(@Param("tagName") String tagName);

    Optional<Asset> findByEngravedNumber(@Param("tagName") String tagName);

    /*
     * Duplicate guards for the bulk import.
     *
     * <p>Case-insensitive because an engraved number is transcribed by hand from a physical label,
     * and "PBL/IT/0412" and "pbl/it/0412" are the same asset however they were typed.
     *
     * <p>Without these, re-uploading a spreadsheet duplicated the whole estate — and a retry after a
     * partial failure is the normal way a migration goes, not an edge case.
     */
    boolean existsByEngravedNumberIgnoreCase(String engravedNumber);

    boolean existsBySerialNumberIgnoreCase(String serialNumber);

    List<Asset> findByStockId(Long stockId);

    List<Asset> findByAssetTypeId(@Param("assetTypeId") Long assetTypeId);

    List<Asset> findByAssetTypeIdAndCreateDateBetween(
            @Param("assetTypeId") Long assetTypeId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // ── Store-aware visibility (§11-12): serialized assets currently held by a store ──
    List<Asset> findByCurrentStore_Id(Long storeId);

    List<Asset> findByCurrentStore_StoreType(pride.bank.erp.store.StoreType storeType);

    /**
     * Assets on the register per order line: {@code [stockId, commodityId, count]}. Disposed assets
     * are counted too — the question this answers is whether a record was ever created for a
     * received unit, and a later end-of-life disposal does not undo that.
     */
    @Query("""
            SELECT a.stock.id, a.commodity.id, COUNT(a)
            FROM Asset a
            WHERE a.stock IS NOT NULL AND a.commodity IS NOT NULL
              AND a.deleted = false
            GROUP BY a.stock.id, a.commodity.id
            """)
    List<Object[]> countAssetsByStockAndCommodity();

    /**
     * Assets filtered by where they sit in the organisation.
     *
     * <p>For an asset held by a person, "which department is this in?" is answered by the holder's
     * department, not by a store — which is why there is no store per user or per unit. Any
     * combination of the three may be null, meaning "don't filter on it".
     */
    // LEFT JOINs, not path navigation: writing `a.assignedTo.department.id` would make Hibernate
    // emit an INNER JOIN on assignedTo, silently dropping every asset sitting in a store — which is
    // most of them — even when no department filter was asked for.
    @Query("""
            SELECT a FROM Asset a
            LEFT JOIN a.assignedTo holder
            LEFT JOIN holder.department dept
            LEFT JOIN holder.unit unit
            WHERE a.disposed = false
              AND a.deleted = false
              AND (:branchId IS NULL OR a.branch.id = :branchId)
              AND (:departmentId IS NULL OR dept.id = :departmentId)
              AND (:unitId IS NULL OR unit.id = :unitId)
            """)
    List<Asset> findByOrganisationScope(@Param("branchId") Long branchId,
                                        @Param("departmentId") Long departmentId,
                                        @Param("unitId") Long unitId);

    /** Everything a given person currently holds. */
    /**
     * How many soft-deleted assets of one category are within the caller's reach.
     *
     * <p>Answers one question for the UI: whether to offer the "Deleted records" view at all. A chip
     * that opens an empty page is worse than no chip.
     *
     * <p>Scoped the same way the listing is — the asset's own branch <b>or</b> its holder's, and the
     * holder alone at SELF — so the number always matches what the view would actually show. A count
     * that ignored scope would promise a Kampala manager records belonging to Gulu.
     *
     * <p>Explicit LEFT JOINs: as dotted paths these would inner-join and drop every deleted asset
     * with no branch or no holder, which is most of them once a record has been taken out of the
     * register. That is trap #1 in CLAUDE.md.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(a) FROM Asset a
            LEFT JOIN a.assetType at
            LEFT JOIN a.branch ab
            LEFT JOIN a.assignedTo holder
            LEFT JOIN holder.branch hb
            WHERE a.deleted = true
              AND (:assetTypeId IS NULL OR at.id = :assetTypeId)
              AND (:scopedBranchId IS NULL
                   OR ab.id = :scopedBranchId
                   OR hb.id = :scopedBranchId)
              AND (:scopedOwnerId IS NULL OR holder.id = :scopedOwnerId)
            """)
    long countDeletedInScope(
            @Param("assetTypeId") Long assetTypeId,
            @Param("scopedBranchId") Long scopedBranchId,
            @Param("scopedOwnerId") Long scopedOwnerId);

    List<Asset> findByAssignedTo_IdAndDisposedFalse(Long userId);

    /** Assets held in a store, per branch: {@code [branchId, count]}. Excludes courier custody. */
    @Query("""
            SELECT a.currentStore.location.id, COUNT(a)
            FROM Asset a
            WHERE a.currentStore IS NOT NULL
              AND a.deleted = false
              AND a.disposed = false
              AND a.currentStore.storeType <> pride.bank.erp.store.StoreType.COURIER
            GROUP BY a.currentStore.location.id
            """)
    List<Object[]> countAssetsInStoreByBranch();
}
