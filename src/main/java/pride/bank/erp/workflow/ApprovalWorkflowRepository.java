package pride.bank.erp.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, Long> {

    /**
     * Finds active workflows matching the given category, branch, scope, and requester roles,
     * ordered from most specific to least specific then by priority ascending.
     *
     * <p>Semantics:
     * <ul>
     *   <li>Workflow matches a category if its {@code assetTypes} set is empty,
     *       or contains the given {@code assetTypeId}.</li>
     *   <li>Workflow matches a branch if its {@code branchId} is null, or equals
     *       the given {@code branchId}.</li>
     *   <li>Workflow scope must equal {@code ALL} or the given scope.</li>
     *   <li>Workflow matches a requester role set if its {@code requesterRoles} is empty,
     *       or intersects the given {@code requesterRoleIds}.</li>
     * </ul>
     */
    @Query("""
            SELECT DISTINCT w FROM ApprovalWorkflow w
            LEFT JOIN w.assetTypes at
            LEFT JOIN w.requesterRoles rr
            WHERE w.active = true
              AND (w.branchId = :branchId OR w.branchId IS NULL)
              AND (w.branchScope = :scope OR w.branchScope = pride.bank.erp.workflow.WorkflowBranchScope.ALL)
              AND (
                    SIZE(w.assetTypes) = 0
                 OR (:assetTypeId IS NOT NULL AND at.id = :assetTypeId)
                  )
              AND (
                    SIZE(w.requesterRoles) = 0
                 OR (:requesterRoleIds IS NOT NULL AND rr.id IN :requesterRoleIds)
                  )
            ORDER BY
              CASE WHEN w.branchId IS NOT NULL AND SIZE(w.assetTypes) > 0 THEN 1
                   WHEN w.branchId IS NOT NULL THEN 2
                   WHEN SIZE(w.assetTypes) > 0 THEN 3
                   ELSE 4 END ASC,
              w.priority ASC
            """)
    List<ApprovalWorkflow> findActiveWorkflowsForRequest(
            @Param("branchId") Long branchId,
            @Param("assetTypeId") Long assetTypeId,
            @Param("scope") WorkflowBranchScope scope,
            @Param("requesterRoleIds") Collection<Long> requesterRoleIds
    );

    List<ApprovalWorkflow> findAllByOrderByPriorityAsc();
}
