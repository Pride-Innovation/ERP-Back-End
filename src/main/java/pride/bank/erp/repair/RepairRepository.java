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
     * Every repair, optionally narrowed by status, asset category and start date.
     *
     * <p>Added for the maintenance report, which asks "what has been repaired" across the estate.
     * Until now repairs could only be read one asset at a time, so there was no way to answer that
     * without walking the whole asset register.
     *
     * <p>Each filter is null-tolerant, so one query serves every combination rather than needing a
     * method per permutation.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT r FROM Repair r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:assetTypeId IS NULL OR r.asset.assetType.id = :assetTypeId)
              AND (:from IS NULL OR r.repairStartDate >= :from)
              AND (:to IS NULL OR r.repairStartDate <= :to)
            """)
    Page<Repair> findForReport(
            @Param("status") String status,
            @Param("assetTypeId") Long assetTypeId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            Pageable pageable);
}
