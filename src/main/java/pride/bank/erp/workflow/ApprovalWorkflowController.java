package pride.bank.erp.workflow;

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
    @GetMapping("/resolve")
    public ResponseEntity<ResolvedApprovalWorkflowDTO> resolve(
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "requesterId", required = false) Long requesterId
    ) {
        return ResponseEntity.ok(resolveService.resolve(categoryId, branchId, requesterId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApprovalWorkflowDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApprovalWorkflowDTO> create(@RequestBody ApprovalWorkflowDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApprovalWorkflowDTO> update(@PathVariable Long id,
                                                       @RequestBody ApprovalWorkflowDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<ApprovalWorkflowDTO> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(service.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
