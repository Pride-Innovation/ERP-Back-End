package pride.bank.erp.movement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovementRepository extends JpaRepository<Movement, Long> {

    /**
     * Movements touching a branch — as origin or destination.
     *
     * <p>A movement belongs to both ends, so a branch user must see one arriving as well as one they
     * sent. Either end can be a store or a person: a return leaves from a user rather than a store,
     * and a fulfilment ends with a requester rather than a store.
     *
     * <p><b>The joins must be explicit and LEFT.</b> Written as dotted paths
     * ({@code m.sourceStore.location.id}) JPQL emits INNER joins, so a row survived only if all four
     * associations were non-null — and no movement ever has all four. The query silently returned
     * nothing for every branch-scoped user, which read as "there are no movements" rather than as a
     * fault. It went unnoticed because anyone with {@code VIEW_ALL_BRANCHES} takes the unscoped
     * {@code findAll} path and never runs this.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT m FROM Movement m
            LEFT JOIN m.sourceStore ss
            LEFT JOIN ss.location ssl
            LEFT JOIN m.destStore ds
            LEFT JOIN ds.location dsl
            LEFT JOIN m.sourceUser su
            LEFT JOIN su.branch sub
            LEFT JOIN m.recipientUser ru
            LEFT JOIN ru.branch rub
            WHERE ssl.id = :branchId
               OR dsl.id = :branchId
               OR sub.id = :branchId
               OR rub.id = :branchId
            """)
    org.springframework.data.domain.Page<Movement> findForBranch(
            @org.springframework.data.repository.query.Param("branchId") Long branchId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Loads one movement by id — always use this instead of {@code findById}.
     *
     * <p>{@code findById} resolves every EAGER association in a single select. Movement has nine of
     * them, six being {@code User}, and a user drags in title / acting title / branch / department /
     * unit / roles while {@code Title.reportsTo} is itself EAGER and self-referencing. That plan
     * joins ~70 tables (title alone 12 times) and MySQL refuses anything over 61, so every
     * {@code findById} call failed with "Too many tables" — taking the detail view and every
     * lifecycle action (dispatch / receive / complete / cancel / approve) down with it. The error
     * surfaced before the row was even read, so a missing id returned 500 rather than 404.
     *
     * <p>A JPQL query loads the root row first and fetches the eager associations as follow-up
     * selects, which is why the list endpoints never hit the limit. The real cure is trimming those
     * EAGER mappings; until then every read must come through here.
     */
    @org.springframework.data.jpa.repository.Query("SELECT m FROM Movement m WHERE m.id = :id")
    java.util.Optional<Movement> findDetailById(
            @org.springframework.data.repository.query.Param("id") Long id);

    /**
     * The most recent movement of a given type carrying this asset. Used to recover where a repaired
     * asset must go back to, from the repair transfer that took it away.
     */
    /**
     * The most recent movement of any of these types carrying this asset.
     *
     * <p>Repair and disposal both take an asset away from the branch that owns it, and both need the
     * same question answered — "where did this come from" — so they are asked together rather than
     * once per type. Newest wins, so an asset repaired twice is not claimed by whoever held it two
     * repairs ago.
     */
    java.util.Optional<Movement> findFirstByItems_Asset_IdAndMovementTypeInOrderByIdDesc(
            Long assetId, java.util.Collection<MovementType> movementTypes);

    java.util.Optional<Movement> findFirstByItems_Asset_IdAndMovementTypeOrderByIdDesc(
            Long assetId, MovementType movementType);

    /**
     * Where each of these assets came from, newest movement first.
     *
     * <h2>Why a listing needs this</h2>
     * An asset's {@code branch} says where it <em>is</em>. Once it moves into the bank-wide IT or
     * Disposal store — both held at Head Office — that becomes Head Office, because Head Office
     * genuinely has custody. The branch it belongs to survives only here, as the
     * {@code originLocation} captured on the movement that took it away, which is also what the
     * return leg aims at.
     *
     * <p>So a branch asking "what of mine is away being repaired" can only be answered from this
     * column. Fetched for a whole page of assets at once rather than per row: the alternative is a
     * query per asset inside a filter.
     *
     * <h2>The same rule as {@code MovementService#branchLastSentAwayFrom}, batched</h2>
     * Only the movements that send an asset <em>away</em> count — a repair transfer or a disposal
     * transfer — and the branch is the recorded {@code originLocation}, else the source store's
     * location, because a disposal records no origin.
     *
     * <p>It used to take the newest movement of <em>any</em> kind that happened to carry an origin.
     * Only a repair transfer sets one, so returns, disposals and reassignments were skipped over and
     * an asset kept the branch of its last repair for good — after it had come home, and after it had
     * moved on. Two copies of one rule had drifted; the detail page's copy was the right one.
     *
     * <p>Whether the asset is away <em>now</em> is not asked here: the caller filters on status first
     * ({@code AssetService.isAwayFromItsBranch}), exactly as the detail page does.
     *
     * <p>The two branch joins are LEFT (trap #1): either may be missing and the other still answers.
     * A movement where both are missing gives no row rather than a null.
     *
     * @return rows of {@code [assetId, branchId]}, newest movement first; the caller keeps the first
     *         row seen per asset.
     */
    @Query("SELECT a.id, COALESCE(ol.id, sl.id) FROM Movement m "
            + "JOIN m.items i JOIN i.asset a "
            + "LEFT JOIN m.originLocation ol LEFT JOIN m.sourceStore ss LEFT JOIN ss.location sl "
            + "WHERE a.id IN :assetIds "
            + "AND m.movementType IN (pride.bank.erp.movement.MovementType.REPAIR_TRANSFER, "
            + "                       pride.bank.erp.movement.MovementType.DISPOSAL_TRANSFER) "
            + "AND (ol.id IS NOT NULL OR sl.id IS NOT NULL) "
            + "ORDER BY m.id DESC")
    List<Object[]> findOriginLocationsForAssets(@Param("assetIds") java.util.Collection<Long> assetIds);

    /**
     * Whose write-off each asset was: the branch on its newest disposal transfer, else that
     * transfer's source store's branch - the Disposal report's "Disposed From" column, read by the
     * same rule as its scope and Branch filter ({@code AssetSearchDao#disposedFrom}).
     *
     * @return rows of {@code [assetId, branchId]}, newest disposal first; keep the first per asset
     */
    @Query("SELECT a.id, COALESCE(ol.id, sl.id) FROM Movement m "
            + "JOIN m.items i JOIN i.asset a "
            + "LEFT JOIN m.originLocation ol LEFT JOIN m.sourceStore ss LEFT JOIN ss.location sl "
            + "WHERE a.id IN :assetIds "
            + "AND m.movementType = pride.bank.erp.movement.MovementType.DISPOSAL_TRANSFER "
            + "ORDER BY m.id DESC")
    List<Object[]> findDisposingBranchesForAssets(@Param("assetIds") java.util.Collection<Long> assetIds);

    /**
     * Which of these assets are on a movement not yet completed or cancelled - committed somewhere,
     * even if nothing on the asset has changed yet. {@code LoanerAvailability} reads it.
     */
    /**
     * The assets this person handed over for repair or disposal that are still away, each with the
     * transfer that took it: the "Away" section of My Items.
     *
     * <p>The same rule as {@code MovementService#holderLastSentAwayFrom}, batched: the <b>newest</b>
     * repair or disposal transfer of the asset decides, and its {@code originHolder} - captured from
     * the active assignment when the transfer was raised - must be this person. Whether it is away now
     * is the caller's status list, as everywhere else ({@code AssetService.AWAY_FROM_BRANCH_CODES}).
     *
     * <p>The inner joins are deliberate: each one is to something the predicate requires.
     *
     * @return rows of {@code [Asset, Movement]}
     */
    @Query("SELECT a, m FROM Movement m JOIN m.items i JOIN i.asset a JOIN a.assetStatus st "
            + "WHERE m.originHolder.id = :userId "
            + "AND m.movementType IN (pride.bank.erp.movement.MovementType.REPAIR_TRANSFER, "
            + "                       pride.bank.erp.movement.MovementType.DISPOSAL_TRANSFER) "
            + "AND a.deleted = false AND st.status IN :awayCodes "
            + "AND m.id = (SELECT MAX(m2.id) FROM Movement m2 JOIN m2.items i2 "
            + "            WHERE i2.asset.id = a.id "
            + "            AND m2.movementType IN (pride.bank.erp.movement.MovementType.REPAIR_TRANSFER, "
            + "                                    pride.bank.erp.movement.MovementType.DISPOSAL_TRANSFER)) "
            + "ORDER BY m.id DESC")
    List<Object[]> findAwayAssetsSentBy(@Param("userId") Long userId,
                                         @Param("awayCodes") java.util.Collection<String> awayCodes);

    /**
     * Movements not yet completed or cancelled that are on their way to this person: addressed to
     * them, or fulfilling a request they raised. These are the "a movement I am party to" limbs that
     * point at the person receiving.
     *
     * <p>Both are LEFT joins (trap #1): a movement has either, or neither, and an inner join on one
     * would drop every movement that has only the other.
     */
    @Query("SELECT DISTINCT m FROM Movement m "
            + "LEFT JOIN m.recipientUser ru LEFT JOIN m.request r LEFT JOIN r.requester rq "
            + "WHERE (ru.id = :userId OR rq.id = :userId) "
            + "AND m.status NOT IN (pride.bank.erp.movement.MovementStatus.COMPLETED, "
            + "                     pride.bank.erp.movement.MovementStatus.CANCELLED) "
            + "ORDER BY m.id DESC")
    List<Movement> findOpenMovementsComingTo(@Param("userId") Long userId);

    /**
     * Consumables promised out of a store by movements that have not drawn them yet, per commodity.
     *
     * <p>A movement draws from its source store at custody handoff (inter-location) or at completion
     * (intra-location), so until {@code custodyTransferSettled} its quantity is still on that store's
     * shelf and spoken for. Mirrors where {@code InventoryService} will debit, so "available" and the
     * debit can never disagree.
     *
     * @return rows of {@code [commodityId, quantity]}
     */
    @Query("SELECT i.commodity.id, SUM(i.quantity) FROM Movement m JOIN m.items i "
            + "WHERE m.sourceStore.id = :storeId "
            + "AND m.custodyTransferSettled = false AND m.inventorySettled = false "
            + "AND m.status NOT IN (pride.bank.erp.movement.MovementStatus.COMPLETED, "
            + "                     pride.bank.erp.movement.MovementStatus.CANCELLED) "
            + "GROUP BY i.commodity.id")
    List<Object[]> findPromisedFromSource(@Param("storeId") Long storeId);

    /**
     * Consumables landed in a store by a consignment and waiting for their recipient to sign:
     * {@code applyConsignmentArrival} credited them, and completion will draw them back out
     * ({@code MovementService#debitSourceFor}). Spoken for in the meantime.
     *
     * @return rows of {@code [commodityId, quantity]}
     */
    @Query("SELECT i.commodity.id, SUM(i.quantity) FROM Movement m JOIN m.items i JOIN m.consignment c "
            + "WHERE c.landingStore.id = :storeId "
            + "AND c.status = pride.bank.erp.consignment.ConsignmentStatus.ARRIVED "
            + "AND m.inventorySettled = false "
            + "AND m.status NOT IN (pride.bank.erp.movement.MovementStatus.COMPLETED, "
            + "                     pride.bank.erp.movement.MovementStatus.CANCELLED) "
            + "GROUP BY i.commodity.id")
    List<Object[]> findPromisedAtLanding(@Param("storeId") Long storeId);

    /**
     * Movements not yet completed or cancelled carrying any of these assets, as
     * {@code [assetId, movementId]} - so a refusal can name the movement an asset is already on.
     */
    @Query("SELECT a.id, m.id FROM Movement m JOIN m.items i JOIN i.asset a "
            + "WHERE a.id IN :assetIds "
            + "AND m.status NOT IN (pride.bank.erp.movement.MovementStatus.COMPLETED, "
            + "                     pride.bank.erp.movement.MovementStatus.CANCELLED)")
    List<Object[]> findOpenMovementsCarrying(@Param("assetIds") java.util.Collection<Long> assetIds);

    @Query("SELECT DISTINCT a.id FROM Movement m JOIN m.items i JOIN i.asset a "
            + "WHERE a.id IN :assetIds "
            + "AND m.status NOT IN (pride.bank.erp.movement.MovementStatus.COMPLETED, "
            + "                     pride.bank.erp.movement.MovementStatus.CANCELLED)")
    List<Long> findAssetIdsOnOpenMovements(@Param("assetIds") java.util.Collection<Long> assetIds);

    /** The open movements carrying this asset, newest first - for saying which one it is on. */
    @Query("SELECT m FROM Movement m JOIN m.items i "
            + "WHERE i.asset.id = :assetId "
            + "AND m.status NOT IN (pride.bank.erp.movement.MovementStatus.COMPLETED, "
            + "                     pride.bank.erp.movement.MovementStatus.CANCELLED) "
            + "ORDER BY m.id DESC")
    List<Movement> findOpenMovementsForAsset(@Param("assetId") Long assetId);

    /*
     * findTempReplacementsForUser is gone, deliberately. It answered "which loaner is out with this
     * user" from the movements - the newest TEMP_REPLACEMENT not cancelled - and a movement only says
     * a loaner was sent. Once that loaner came back, it still matched, so the automatic return built
     * on it kept "returning" loaners already in the pool. Who holds a loaner is an open temporary
     * assignment: AssetAssignmentHistoryRepository#findOpenLoansForUser.
     */

    /**
     * A hand-back of this asset that is still under way - initiated, awaiting approval, or on the
     * road. A loaner with one of these is already being returned, so it is not offered again.
     */
    java.util.Optional<Movement> findFirstByItems_Asset_IdAndMovementTypeAndStatusNotInOrderByIdDesc(
            Long assetId, MovementType movementType, java.util.Collection<MovementStatus> statuses);

    /** Everything riding on one journey. */
    List<Movement> findByConsignment_Id(Long consignmentId);

    List<Movement> findByRequest_Id(Long requestId);

    List<Movement> findByRepair_Id(Long repairId);

    Page<Movement> findByStatus(MovementStatus status, Pageable pageable);

    Page<Movement> findBySourceStore_Location_Id(Long locationId, Pageable pageable);

    Page<Movement> findByDestStore_Location_Id(Long locationId, Pageable pageable);

    /** A user's movement-approval inbox: movements in DRAFT currently assigned to them. */
    Page<Movement> findByCurrentApprover_IdAndStatus(Long approverId, MovementStatus status, Pageable pageable);
}
