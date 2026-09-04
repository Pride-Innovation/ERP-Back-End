package pride.bank.erp.workflow;

import org.springframework.security.access.prepost.PreAuthorize;
import pride.bank.erp.helper.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

@RestController
@RequestMapping(GENERAL_ROUTE + "/approval-workflows")
@RequiredArgsConstructor
public class ApprovalWorkflowController {

    private final ApprovalWorkflowService service;
    private final WorkflowResolveService resolveService;

    /*
     * Approval routing is configuration, so it answers to the settings permissions.
     *
     * Every one of these fell through to `.anyRequest().authenticated()`, which meant any signed-in
     * user could rewrite who approves what — add a step, retarget one at themselves, or delete the
     * workflow so requests skipped approval altogether. That is a larger power than any single
     * approval permission grants, and it was the one thing nothing checked.
     *
     * Reads are separated from writes: seeing how a request will be routed is useful to anyone
     * administering the system, while changing it is not.
     */
    @PreAuthorize("hasAuthority('" + Constants.READ_SETTING + "')")
    @GetMapping
    public ResponseEntity<List<ApprovalWorkflowDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    /**
     * Resolves which workflow will run for the given (category, branch, requester) tuple
     * and returns per-step approver / fallback / skip metadata for UI preview.
     *
     * <p>When no workflow matches, returns 200 OK with {@code workflowId = null} and a
     * human-readable {@code noMatchReason}.
     */
    @PreAuthorize("hasAuthority('" + Constants.READ_SETTING + "')")
    @GetMapping("/resolve")
    public ResponseEntity<ResolvedApprovalWorkflowDTO> resolve(
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "requesterId", required = false) Long requesterId
    ) {
        return ResponseEntity.ok(resolveService.resolve(categoryId, branchId, requesterId));
    }

    @PreAuthorize("hasAuthority('" + Constants.READ_SETTING + "')")
    @GetMapping("/{id}")
    public ResponseEntity<ApprovalWorkflowDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PreAuthorize("hasAuthority('" + Constants.UPDATE_SETTING + "')")
    @PostMapping
    public ResponseEntity<ApprovalWorkflowDTO> create(@RequestBody ApprovalWorkflowDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @PreAuthorize("hasAuthority('" + Constants.UPDATE_SETTING + "')")
    @PutMapping("/{id}")
    public ResponseEntity<ApprovalWorkflowDTO> update(@PathVariable Long id,
                                                       @RequestBody ApprovalWorkflowDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PreAuthorize("hasAuthority('" + Constants.UPDATE_SETTING + "')")
    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<ApprovalWorkflowDTO> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(service.toggleActive(id));
    }

    @PreAuthorize("hasAuthority('" + Constants.UPDATE_SETTING + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
