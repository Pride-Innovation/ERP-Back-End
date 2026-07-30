package pride.bank.erp.movement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovementRepository extends JpaRepository<Movement, Long> {

    /**
     * Movements touching a branch — as origin or destination.
     *
     * <p>A movement belongs to both ends, so a branch user must see one arriving as well as one they
     * sent. The source can be a user rather than a store (a return), hence the third clause.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT m FROM Movement m
            WHERE m.sourceStore.location.id = :branchId
               OR m.destStore.location.id = :branchId
               OR m.sourceUser.branch.id = :branchId
               OR m.recipientUser.branch.id = :branchId
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
    java.util.Optional<Movement> findFirstByItems_Asset_IdAndMovementTypeOrderByIdDesc(
            Long assetId, MovementType movementType);

    List<Movement> findByRequest_Id(Long requestId);

    List<Movement> findByRepair_Id(Long repairId);

    Page<Movement> findByStatus(MovementStatus status, Pageable pageable);

    Page<Movement> findBySourceStore_Location_Id(Long locationId, Pageable pageable);

    Page<Movement> findByDestStore_Location_Id(Long locationId, Pageable pageable);

    /** A user's movement-approval inbox: movements in DRAFT currently assigned to them. */
    Page<Movement> findByCurrentApprover_IdAndStatus(Long approverId, MovementStatus status, Pageable pageable);
}
