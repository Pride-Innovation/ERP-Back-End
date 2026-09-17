package pride.bank.erp.workflowinstance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.request.RequestService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

@RestController
@RequestMapping(GENERAL_ROUTE + "/workflow")
@RequiredArgsConstructor
public class WorkflowInstanceController {

    private final WorkflowEngineService workflowEngineService;
    private final RequestService requestService;

    /**
     * The approval trail for one request: who acted, when, and what they said.
     *
     * <h2>What was open</h2>
     * This carried no {@code @PreAuthorize}, and {@code /workflow/**} appears nowhere in
     * {@code SecurityConfiguration} — so it fell through to {@code .anyRequest().authenticated()}
     * and <b>any signed-in user could read the full approval history of any request by id</b>,
     * including approver names, timings and comments. That is a larger disclosure than the request
     * itself: it says who reviewed what and when.
     *
     * <p>Gated on {@code READ_REQUEST}, and scoped through the request it belongs to — the trail is
     * no less sensitive than the record, so it answers to the same rule, including the exception
     * that lets a routed approver read one from another branch.
     */
    @PreAuthorize("hasAuthority('" + Constants.READ_REQUEST + "')")
    @GetMapping("/step-logs/{requestId}")
    public ResponseEntity<List<StepLogDTO>> getStepLogs(@PathVariable Long requestId)
            throws pride.bank.erp.exception.NotFoundException {
        requestService.requireReadableById(requestId);
        return ResponseEntity.ok(workflowEngineService.getStepLogs(requestId));
    }
}
