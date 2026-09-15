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
     * maintenance report handed any holder of READ_ASSET every branch's repair history. It matches
     * the asset's branch or its holder's, the same pair the asset listing uses, so an item assigned
     * to someone at this branch stays visible even when the asset row still points elsewhere.
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
              AND (:scopedBranchId IS NULL
                   OR ab.id = :scopedBranchId
                   OR ahb.id = :scopedBranchId)
            """)
    Page<Repair> findForReport(
            @Param("status") String status,
            @Param("assetTypeId") Long assetTypeId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @org.springframework.data.repository.query.Param("scopedBranchId") Long scopedBranchId,
            Pageable pageable);
}
