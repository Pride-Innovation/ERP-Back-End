package pride.bank.erp.workflowinstance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

@RestController
@RequestMapping(GENERAL_ROUTE + "/workflow")
@RequiredArgsConstructor
public class WorkflowInstanceController {

    private final WorkflowEngineService workflowEngineService;

    @GetMapping("/step-logs/{requestId}")
    public ResponseEntity<List<StepLogDTO>> getStepLogs(@PathVariable Long requestId) {
        return ResponseEntity.ok(workflowEngineService.getStepLogs(requestId));
    }
}
