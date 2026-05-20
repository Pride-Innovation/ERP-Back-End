package pride.bank.erp.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, Long> {

    /**
     * Finds active workflows ordered from most specific to least specific.
     * Precedence: branchId+assetTypeId > branchId only > assetTypeId only > default (both null).
     */
    @Query("""
            SELECT w FROM ApprovalWorkflow w
            WHERE w.active = true
              AND (w.branchId = :branchId OR w.branchId IS NULL)
              AND (w.assetTypeId = :assetTypeId OR w.assetTypeId IS NULL)
              AND (w.branchScope = :scope OR w.branchScope = 'ALL')
            ORDER BY
              CASE WHEN w.branchId IS NOT NULL AND w.assetTypeId IS NOT NULL THEN 1
                   WHEN w.branchId IS NOT NULL THEN 2
                   WHEN w.assetTypeId IS NOT NULL THEN 3
                   ELSE 4 END ASC,
              w.priority ASC
            """)
    List<ApprovalWorkflow> findActiveWorkflowsForRequest(
            @Param("branchId") Long branchId,
            @Param("assetTypeId") Long assetTypeId,
            @Param("scope") WorkflowBranchScope scope
    );

    List<ApprovalWorkflow> findAllByOrderByPriorityAsc();
}
