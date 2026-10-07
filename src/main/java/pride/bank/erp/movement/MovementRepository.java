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
     * <p>Every join is inner and deliberately so — a movement with no origin, or a consumable line
     * with no asset, has no answer to give and should not produce a row. That is the one shape where
     * trap #1 does not apply, because nothing here is optional.
     *
     * @return rows of {@code [assetId, originLocationId]}, newest movement first; the caller keeps
     *         the first row seen per asset.
     */
    @Query("SELECT a.id, ol.id FROM Movement m "
            + "JOIN m.items i JOIN i.asset a JOIN m.originLocation ol "
            + "WHERE a.id IN :assetIds ORDER BY m.id DESC")
    List<Object[]> findOriginLocationsForAssets(@Param("assetIds") java.util.Collection<Long> assetIds);

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
