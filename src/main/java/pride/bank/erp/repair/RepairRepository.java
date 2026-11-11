package pride.bank.erp.repair;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface RepairRepository extends JpaRepository<Repair, Long> {
    Page<Repair> findByAssetId(@Param("assetId") Long assetId, Pageable pageable);

    /**
     * The maintenance record still open on this asset, if any.
     *
     * <p>Newest first, because an asset repaired twice has two records and the return leg is always
     * closing the current one. A derived query rather than JPQL deliberately: every path here is
     * through a non-nullable association, so there is no trap #1 exposure to reason about.
     */
    java.util.Optional<Repair> findFirstByAsset_IdAndStatusOrderByIdDesc(Long assetId, String status);

    /**
     * Every repair, optionally narrowed by status, asset category and start date.
     *
     * <p>Added for the maintenance report, which asks "what has been repaired" across the estate.
     * Until now repairs could only be read one asset at a time, so there was no way to answer that
     * without walking the whole asset register.
     *
     * <p>Each filter is null-tolerant, so one query serves every combination rather than needing a
     * method per permutation.
     */
    /*
     * Explicit LEFT JOINs, and a branch scope.
     *
     * Two changes to what was here. `r.asset.assetType.id` was a dotted path, which JPA compiles to
     * INNER joins that sit in the FROM clause and filter before the `:assetTypeId IS NULL` test is
     * ever evaluated — so asking for "every category" silently dropped every repair whose asset has
     * no category. That is trap #1 in CLAUDE.md.
     *
     * And `scopedBranchId` was missing entirely: this answers across the estate, so without it the
     * maintenance report handed any holder of READ_ASSET every branch's repair history.
     *
     * A repair belongs to the branch that sent the asset - and was keyed on the wrong thing.
     *
     * It matched the asset's current branch or its holder's. On the bench neither is the sender's:
     * receipt rewrites `asset.branch` to Head Office and the transfer closes the assignment, so a Gulu
     * branch saw none of its own repairs while they were open - measured, all nine open records were
     * Gulu's and every one read Head Office. The repair record is that branch's history, so it now
     * answers to the transfer that opened it: its `originLocation`, else its source store's location
     * (the same pair `MovementService.branchLastSentAwayFrom` reads). Unlike a store listing, this does
     * not lapse when the asset comes home - the record is history, and it stays the sender's.
     *
     * A record with no opening transfer (none today) falls back to the old pair. The same branch rule
     * serves the caller's scope and an all-branches viewer's Branch filter, which arrive as one
     * resolved id, and is the one `findOwningBranches` reports for the Branch column.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT r FROM Repair r
            LEFT JOIN r.asset a
            LEFT JOIN a.assetType at
            LEFT JOIN a.branch ab
            LEFT JOIN a.assignedTo ah
            LEFT JOIN ah.branch ahb
            WHERE (:status IS NULL OR r.status = :status)
              AND (:assetTypeId IS NULL OR at.id = :assetTypeId)
              AND (:from IS NULL OR r.repairStartDate >= :from)
              AND (:to IS NULL OR r.repairStartDate <= :to)
              AND (:technicianType IS NULL OR r.technicianType = :technicianType)
              AND (:unrecordedTechnician = false OR r.technicianType IS NULL)
              AND (:scopedBranchId IS NULL
                   OR (SELECT MAX(COALESCE(ol.id, sl.id)) FROM Movement m
                         LEFT JOIN m.originLocation ol LEFT JOIN m.sourceStore ss LEFT JOIN ss.location sl
                        WHERE m.repair = r
                          AND m.movementType = pride.bank.erp.movement.MovementType.REPAIR_TRANSFER) = :scopedBranchId
                   OR ((SELECT MAX(COALESCE(ol2.id, sl2.id)) FROM Movement m2
                         LEFT JOIN m2.originLocation ol2 LEFT JOIN m2.sourceStore ss2 LEFT JOIN ss2.location sl2
                        WHERE m2.repair = r
                          AND m2.movementType = pride.bank.erp.movement.MovementType.REPAIR_TRANSFER) IS NULL
                       AND (ab.id = :scopedBranchId OR ahb.id = :scopedBranchId)))
            """)
    Page<Repair> findForReport(
            @Param("status") String status,
            @Param("assetTypeId") Long assetTypeId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @Param("technicianType") TechnicianType technicianType,
            @Param("unrecordedTechnician") boolean unrecordedTechnician,
            @Param("scopedBranchId") Long scopedBranchId,
            Pageable pageable);

    /**
     * The branch each repair belongs to - the one its opening transfer sent the asset from - for the
     * report's Branch column. One query for the page. Same rule as {@code findForReport}'s branch
     * clause; a repair with no opening transfer gets no row and the caller falls back to the asset.
     *
     * @return rows of {@code [repairId, branchId]}, newest transfer first
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT rr.id, COALESCE(ol.id, sl.id) FROM Movement m
            JOIN m.repair rr
            LEFT JOIN m.originLocation ol LEFT JOIN m.sourceStore ss LEFT JOIN ss.location sl
            WHERE rr.id IN :repairIds
              AND m.movementType = pride.bank.erp.movement.MovementType.REPAIR_TRANSFER
              AND (ol.id IS NOT NULL OR sl.id IS NOT NULL)
            ORDER BY m.id DESC
            """)
    java.util.List<Object[]> findOwningBranches(@Param("repairIds") java.util.Collection<Long> repairIds);
}
