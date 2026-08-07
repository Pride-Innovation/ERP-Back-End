package pride.bank.erp.consignment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsignmentRepository extends JpaRepository<Consignment, Long> {

    /**
     * Loads one consignment by id without touching its movements.
     *
     * <p>A JPQL query rather than {@code findById} for the same reason movements need one: resolving
     * eager associations in a single select drags {@code User} → title → {@code reportsTo} into a
     * join plan that MySQL refuses past 61 tables. Keeping the movement collection LAZY keeps this
     * one small, but the root row still comes through here by convention.
     */
    @Query("SELECT c FROM Consignment c WHERE c.id = :id")
    Optional<Consignment> findDetailById(@Param("id") Long id);

    Page<Consignment> findByStatus(ConsignmentStatus status, Pageable pageable);

    Page<Consignment> findByDestLocation_Id(Long branchId, Pageable pageable);

    /**
     * Consignments still being assembled for a given route, so a new movement can join one already
     * heading where it is going instead of starting another.
     */
    List<Consignment> findByStatusAndSourceLocation_IdAndDestLocation_Id(
            ConsignmentStatus status, Long sourceLocationId, Long destLocationId);

    /**
     * Journeys touching a branch, in either direction.
     *
     * <p>Explicit LEFT joins, not dotted paths: JPQL turns {@code c.sourceLocation.id} into an INNER
     * join, so a consignment missing either end would drop out of its own branch's listing. Both
     * columns are nullable, and the same shape written as dotted paths on {@code Movement} silently
     * returned nothing for every branch-scoped user.
     */
    @Query("""
            SELECT c FROM Consignment c
            LEFT JOIN c.sourceLocation sl
            LEFT JOIN c.destLocation dl
            WHERE sl.id = :branchId
               OR dl.id = :branchId
            """)
    Page<Consignment> findForBranch(@Param("branchId") Long branchId, Pageable pageable);
}
