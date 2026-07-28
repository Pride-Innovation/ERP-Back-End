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
