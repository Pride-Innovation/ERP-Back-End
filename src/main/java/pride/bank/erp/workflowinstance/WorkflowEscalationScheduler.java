package pride.bank.erp.workflowinstance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hourly sweep that re-sends approval reminders for pending workflow steps.
 *
 * <p>For each in-progress request, the current step's approver is reminded once the step's
 * {@code escalationHours} have elapsed since it became pending (and again every window after,
 * until the step is actioned). Each instance is processed in its own transaction via
 * {@link WorkflowEngineService#sendReminderIfDue(Long)} so a single failure can't abort the
 * rest of the sweep.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowEscalationScheduler {

    private final RequestWorkflowInstanceRepository instanceRepository;
    private final WorkflowEngineService workflowEngineService;

    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        List<RequestWorkflowInstance> active = instanceRepository.findByStatus(InstanceStatus.IN_PROGRESS);
        log.debug("Escalation sweep: {} in-progress workflow instance(s)", active.size());
        for (RequestWorkflowInstance instance : active) {
            try {
                workflowEngineService.sendReminderIfDue(instance.getId());
            } catch (Exception e) {
                log.warn("Escalation reminder failed for instance {}: {}", instance.getId(), e.getMessage());
            }
        }
    }
}
