package pride.bank.erp.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pride.bank.erp.asset.AssetTypeSummaryView;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link StoreBalance} (on-hand quantities). Formerly {@code StoreRepository};
 * renamed when the {@code Store} name was repurposed for the store container. The underlying table
 * is still {@code store}, so native queries are unchanged.
 *
 * <p><b>Courier custody caveat:</b> a {@link StoreType#COURIER} custody store is anchored to the
 * Head Office branch (its {@code location} is non-null only to satisfy the schema), so its balances
 * carry {@code branch = Head Office}. That means "one balance per (branch, commodity)" no longer
 * holds — Head Office can have both its ADMIN row and a courier-custody row for the same commodity.
 * Every branch-scoped query below therefore excludes COURIER stores, and single-result lookups are
 * scoped to a specific store (never branch-only), so in-transit custody can neither crash a
 * {@code getSingleResult} nor masquerade as branch stock.
 */
@Repository
public interface StoreBalanceRepository extends JpaRepository<StoreBalance, Long> {

    /**
     * The (unique) balance of a commodity in a branch's store of the given type. Use with
     * {@link StoreType#ADMIN} for availability checks — branch-only lookups are unsafe because a
     * courier-custody row shares the same branch (see class doc).
     */
    Optional<StoreBalance> findByBranchIdAndStore_StoreTypeAndCommodity_Id(
            Long branchId, StoreType storeType, Long commodityId);

    @Query("SELECT s FROM StoreBalance s " +
            "JOIN FETCH s.commodity " +
            "JOIN FETCH s.branch " +
            "WHERE s.branch.id = :branchId " +
            "AND (s.store IS NULL OR s.store.storeType <> pride.bank.erp.store.StoreType.COURIER)")
    List<StoreBalance> findAllByBranchIdWithCommodityAndBranch(@Param("branchId") Long branchId);

    /**
     * The one balance listing, with every filter optional.
     *
     * <p>Replaces four near-identical methods that differed only in which filters they applied. Each
     * combination needed its own method, so adding a fifth filter meant writing eight — which is why
     * the store page's "Stock Name" box had no server behind it and silently did nothing.
     *
     * <p><b>Explicit LEFT JOINs, deliberately.</b> Written as {@code s.commodity.assetType.id} the
     * optional filter would still put an INNER JOIN in the FROM clause, and it would filter before
     * the {@code :assetTypeId IS NULL} test was ever evaluated — so asking for "all categories" would
     * silently drop every balance line whose commodity has no category. That is this codebase's most
     * repeated bug and it is exactly the shape a nullable optional filter invites.
     *
     * <p>Courier custody is excluded only when no store type is named. Asking for COURIER explicitly
     * is a deliberate act and should return what it asked for.
     *
     * <p>{@code stockLevel} applies each line's <b>own</b> reorder threshold — the same rule as
     * {@code findLowStockOnHand} and {@code BranchStoreOverview}, so the store page and the low-stock
     * monitor cannot disagree about the same commodity. {@code unmonitored} is a real answer, not an
     * absence: a line with no threshold set is never flagged however little of it is on hand, and
     * being able to list those is how somebody finds the gaps.
     */
    @Query("""
            SELECT s FROM StoreBalance s
            LEFT JOIN s.commodity c
            LEFT JOIN c.assetType at
            LEFT JOIN s.store st
            WHERE s.branch.id = :branchId
              AND (:storeType IS NOT NULL
                   OR st IS NULL
                   OR st.storeType <> pride.bank.erp.store.StoreType.COURIER)
              AND (:storeType IS NULL OR st.storeType = :storeType)
              AND (:assetTypeId IS NULL OR at.id = :assetTypeId)
              AND (:stockName IS NULL
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:stockName AS string), '%')))
              AND (:stockLevel IS NULL
                   OR (:stockLevel = 'low'         AND s.minLevel > 0 AND s.quantity <= s.minLevel)
                   OR (:stockLevel = 'adequate'    AND s.minLevel > 0 AND s.quantity >  s.minLevel)
                   OR (:stockLevel = 'unmonitored' AND s.minLevel = 0))
            """)
    Page<StoreBalance> search(
            @Param("branchId") Long branchId,
            @Param("assetTypeId") Long assetTypeId,
            @Param("storeType") StoreType storeType,
            @Param("stockName") String stockName,
            @Param("stockLevel") String stockLevel,
            Pageable pageable);

    // ── Store-scoped listings: each store page (Admin / IT / Disposal) sees only its own lines.
    // Null-store legacy rows are backfilled to the branch ADMIN store by StoreSeeder on startup,
    // so the store container cannot hide live balances. The four single-purpose listings that used
    // to sit here are superseded by search() above, which does the same work with every filter
    // optional — they are gone rather than deprecated so nothing reaches for one and reintroduces
    // the missing name filter or the missing sort tie-break.
    @Query("SELECT s FROM StoreBalance s " +
            "WHERE s.branch.id = :branchId " +
            "AND s.commodity.assetType.id = :assetTypeId " +
            "AND (s.store IS NULL OR s.store.storeType <> pride.bank.erp.store.StoreType.COURIER)")
    List<StoreBalance> findByBranchAndAssetType(@Param("branchId") Long branchId,
                                                @Param("assetTypeId") Long assetTypeId);

    @Query(value = """
                SELECT
                    at.name AS assetTypeName,
                    SUM(s.quantity) AS totalCount,
                    MAX(s.create_date) AS lastUpdatedDate
                FROM store s
                JOIN commodity c ON c.id = s.commodity_id
                JOIN asset_type at ON at.id = c.asset_type_id
                LEFT JOIN store_container sc ON sc.id = s.store_id
                WHERE at.name = :assetTypeName AND s.quantity > 0
                  AND (sc.store_type IS NULL OR sc.store_type <> 'COURIER')
                GROUP BY at.name
            """, nativeQuery = true)
    AssetTypeSummaryView findAssetTypeSummary(@Param("assetTypeName") String assetTypeName);

    @Query("""
                SELECT c.name AS commodityName, SUM(s.quantity) AS totalQuantity
                FROM StoreBalance s
                JOIN s.commodity c
                JOIN c.assetType at
                WHERE at.name = 'Stationery'
                  AND s.branch.id = :branchId
                  AND (s.store IS NULL OR s.store.storeType <> pride.bank.erp.store.StoreType.COURIER)
                GROUP BY c.name
            """)
    List<CommoditySummary> getStationeryCommoditiesByCurrentMonthByBranch(@Param("branchId") Long branchId);

    /** Balance of a commodity within a specific store container — the unit the ledger mutates. */
    Optional<StoreBalance> findByStore_IdAndCommodity_Id(Long storeId, Long commodityId);

    /** Balances whose container link has not yet been backfilled (used by the one-time migration). */
    List<StoreBalance> findByStoreIsNull();

    // ── On-hand views: courier custody deliberately excluded ─────────────────
    // A COURIER store's location is pinned to Head Office purely to satisfy the NOT NULL on the
    // column, so its balances carry branch = Head Office. Any unfiltered query therefore reports
    // every unit in transit anywhere in the country as Head Office stock, and counts goods that are
    // in a van as available on a shelf. These are the on-hand equivalents of findAll() /
    // findLowStock() / findByStore_Location_Id().

    @Query("SELECT s FROM StoreBalance s WHERE s.store IS NULL OR s.store.storeType <> pride.bank.erp.store.StoreType.COURIER")
    List<StoreBalance> findAllOnHand();

    @Query("SELECT s FROM StoreBalance s WHERE s.store.location.id = :locationId "
            + "AND s.store.storeType <> pride.bank.erp.store.StoreType.COURIER")
    List<StoreBalance> findOnHandByLocation(@Param("locationId") Long locationId);

    @Query("SELECT b FROM StoreBalance b WHERE b.minLevel > 0 AND b.quantity <= b.minLevel "
            + "AND (b.store IS NULL OR b.store.storeType <> pride.bank.erp.store.StoreType.COURIER)")
    List<StoreBalance> findLowStockOnHand();

    /**
     * Per-branch stock position in one query: {@code [branchId, lines, totalQty, lowStockLines]}.
     *
     * <p>Replaces the store landing page's per-branch round trip — it fired one request per branch
     * just to read a count, so 50 branches meant 50 requests on every page load.
     */
    @Query("""
            SELECT s.branch.id,
                   COUNT(s),
                   COALESCE(SUM(s.quantity), 0),
                   SUM(CASE WHEN s.minLevel > 0 AND s.quantity <= s.minLevel THEN 1 ELSE 0 END)
            FROM StoreBalance s
            WHERE s.store IS NULL OR s.store.storeType <> pride.bank.erp.store.StoreType.COURIER
            GROUP BY s.branch.id
            """)
    List<Object[]> summariseByBranch();

    /** Everything a courier is currently carrying — the counterpart the views above leave out. */
    @Query("SELECT s FROM StoreBalance s WHERE s.store.storeType = pride.bank.erp.store.StoreType.COURIER "
            + "AND s.quantity > 0")
    List<StoreBalance> findInTransit();

    // ── Reporting / visibility (§11-12) ──────────────────────────────────────
    List<StoreBalance> findByStore_Id(Long storeId);

    List<StoreBalance> findByStore_Location_Id(Long locationId);

    List<StoreBalance> findByStore_StoreType(StoreType storeType);

    List<StoreBalance> findByStore_Department_Id(Long departmentId);

    /** Low-stock monitoring: balances at or below a threshold (excludes empty by default at callsite). */
    List<StoreBalance> findByQuantityLessThanEqual(int threshold);

    /** Low-stock by each balance's own configured reorder threshold. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT b FROM StoreBalance b WHERE b.minLevel > 0 AND b.quantity <= b.minLevel")
    List<StoreBalance> findLowStock();
}
