package pride.bank.erp.workflowinstance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import pride.bank.erp.permission.Permission;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.title.Title;
import pride.bank.erp.unit.Unit;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.workflow.ApprovalStep;
import pride.bank.erp.workflow.ApprovalWorkflow;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pride.bank.erp.helper.Constants.MANAGE_ALL_BRANCH_REQUESTS;

/**
 * Who is allowed to act on a workflow step.
 *
 * <p>The engine used to ask only what was being done, never by whom. The route guard checks that a
 * caller holds {@code APPROVE_REQUEST}; nothing then checked that <em>this</em> request was waiting
 * on <em>this</em> person. So anyone who could approve anything could approve everything — any
 * request, at any tier, including a step routed to a unit they do not belong to. That made the unit
 * inbox a suggestion rather than an assignment, which is the opposite of what routing a step to a
 * unit is for.
 *
 * <p>The refusing case is the easy half to get right. The three permissive cases below are the ones
 * that matter operationally: a check that refuses too readily strands a request, and a stranded
 * request stops the bank's work with no way for anyone to clear it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StepActorRoutingTest {

    private static final Long REQUEST_ID = 7L;
    private static final Long ROUTED_APPROVER = 10L;
    private static final Long SOMEONE_ELSE = 20L;

    @Mock private RequestWorkflowInstanceRepository instanceRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @Mock private RequestStepLogRepository stepLogRepository;

    @InjectMocks private WorkflowEngineService workflowEngineService;

    private User user(Long id, Unit unit, String... permissions) {
        Set<Permission> held = new HashSet<>();
        for (String name : permissions) held.add(Permission.builder().name(name).build());
        Role role = Role.builder().name("TEST_ROLE").permissions(held).build();
        return User.builder()
                .id(id)
                .firstName("Test")
                .lastName("Person")
                .title(Title.builder().role(role).build())
                .unit(unit)
                .build();
    }

    /**
     * Stands the engine up far enough to reach the actor check and no further.
     *
     * <p>The check runs immediately after the actor is looked up and before the step log is touched,
     * so a refusal leaves nothing behind — which is what {@link #aRefusalWritesNothing()} pins.
     */
    private void routedTo(User routedApprover, Unit routedUnit, User actor) {
        ApprovalStep step = ApprovalStep.builder().stepOrder(1).stepName("Approval").build();
        ApprovalWorkflow workflow = ApprovalWorkflow.builder().steps(List.of(step)).build();

        RequestWorkflowInstance instance = RequestWorkflowInstance.builder()
                .id(1L)
                .workflow(workflow)
                .currentStepOrder(1)
                .status(InstanceStatus.IN_PROGRESS)
                .build();

        Request request = Request.builder()
                .id(REQUEST_ID)
                .currentApprover(routedApprover)
                .currentUnit(routedUnit)
                .build();

        when(instanceRepository.findTopByRequestIdOrderByIdDesc(REQUEST_ID))
                .thenReturn(Optional.of(instance));
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(userRepository.findById(anyLong())).thenReturn(Optional.ofNullable(actor));
        when(stepLogRepository.findPendingByInstanceAndStep(anyLong(), anyInt()))
                .thenReturn(Optional.empty());
    }

    private void act(Long actorId) throws Exception {
        workflowEngineService.processStepAction(REQUEST_ID, actorId, "APPROVED", null);
    }

    /**
     * Asserts only that the actor check let this through.
     *
     * <p>What happens afterwards is advancement's business: the engine goes on to resolve the next
     * step, write logs and send mail, none of which is stood up here. Anything but an
     * {@link AccessDeniedException} means the request got past the rule under test, which is the
     * whole of what these cases claim. Pinning more would make them fail for reasons they are not
     * about, and the permissive cases are exactly the ones that must not become brittle — they are
     * what stops a future tightening from stranding real requests.
     */
    private void assertNotRefused(Long actorId) {
        try {
            act(actorId);
        } catch (AccessDeniedException refused) {
            throw new AssertionError("the actor check refused this, but it should have allowed it: "
                    + refused.getMessage(), refused);
        } catch (Exception advancementFailed) {
            // Got past the check. Everything beyond it is out of scope here.
        }
    }

    // ── The refusals ────────────────────────────────────────────────────────

    @Test
    void someoneElseCannotApproveAStepRoutedToANamedPerson() {
        User routed = user(ROUTED_APPROVER, null);
        User interloper = user(SOMEONE_ELSE, null);
        routedTo(routed, null, interloper);

        assertThatThrownBy(() -> act(SOMEONE_ELSE))
                .as("holding APPROVE_REQUEST admits you to the endpoint; it does not make the "
                        + "request yours to decide")
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("waiting on");
    }

    @Test
    void anOutsiderCannotActOnAStepRoutedToAUnit() {
        Unit adminUnit = Unit.builder().id(1L).name("Admin Unit").build();
        Unit infraUnit = Unit.builder().id(2L).name("Infra Unit").build();
        routedTo(null, adminUnit, user(SOMEONE_ELSE, infraUnit));

        assertThatThrownBy(() -> act(SOMEONE_ELSE))
                .as("routing a step to a unit has to mean something, or the unit inbox is decoration")
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Admin Unit");
    }

    @Test
    void aRefusalWritesNothing() {
        routedTo(user(ROUTED_APPROVER, null), null, user(SOMEONE_ELSE, null));

        assertThatThrownBy(() -> act(SOMEONE_ELSE)).isInstanceOf(AccessDeniedException.class);

        verify(stepLogRepository, never()).save(any());
        verify(requestRepository, never()).save(any());
    }

    // ── The permissive cases, which matter more ─────────────────────────────

    @Test
    void theRoutedApproverProceeds() {
        User routed = user(ROUTED_APPROVER, null);
        routedTo(routed, null, routed);

        assertNotRefused(ROUTED_APPROVER);
    }

    @Test
    void aMemberOfTheRoutedUnitProceeds() {
        Unit adminUnit = Unit.builder().id(1L).name("Admin Unit").build();
        routedTo(null, adminUnit, user(SOMEONE_ELSE, adminUnit));

        // The whole point of routing to a unit: any member may pick it up.
        assertNotRefused(SOMEONE_ELSE);
    }

    @Test
    void aRequestWithNothingRoutedIsNotStranded() {
        // Created before the workflow engine existed, or a step whose approver did not resolve.
        routedTo(null, null, user(SOMEONE_ELSE, null));

        // Refusing here would strand the request permanently, with nobody able to clear it.
        assertNotRefused(SOMEONE_ELSE);
    }

    @Test
    void aSystemDrivenStepWithNoActorIsNotRefused() {
        routedTo(user(ROUTED_APPROVER, null), null, null);

        // Nobody claimed to act, so there is nothing to verify.
        assertNotRefused(null);
    }

    @Test
    void theCrossBranchGrantIsTheOverride() {
        // MANAGE_ALL_BRANCH_REQUESTS is exactly "may act on any branch's requests". The super
        // administrator holds it like anyone else, so it needs no special case of its own.
        routedTo(user(ROUTED_APPROVER, null), null,
                user(SOMEONE_ELSE, null, MANAGE_ALL_BRANCH_REQUESTS));

        assertNotRefused(SOMEONE_ELSE);
    }

    @Test
    void anActingUserStandingInForSomeoneOnLeaveProceeds() {
        // substituteIfOnLeave resolves the acting user into currentApprover when the step is
        // assigned, so by the time anyone acts they are the routed approver on their own account.
        User acting = user(SOMEONE_ELSE, null);
        routedTo(acting, null, acting);

        assertNotRefused(SOMEONE_ELSE);
    }
}
