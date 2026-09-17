/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.movement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pride.bank.erp.appnotification.AppNotificationService;
import pride.bank.erp.auditing.trail.AuditService;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.notification.Email;
import pride.bank.erp.workflow.ApproverChainResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the approval bypass — the one action that moves stock with no approval at all.
 *
 * <p>The bypass is not an endpoint, so no {@code @PreAuthorize} can reach it. It is the
 * {@code proceedWithoutApproval} flag on the create payload, offered when no approver resolves on
 * the initiator's reporting line, and five create paths set it. {@code startApproval} is the single
 * funnel they all pass through, which makes it the only place the rule can be stated once.
 *
 * <p>Before this, anyone who could create a movement could also authorise its own bypass — and
 * before the movement endpoints were guarded at all, that was every authenticated user. The audit
 * trail recorded the bypass as CRITICAL but nothing prevented it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MovementApprovalBypassTest {

    @Mock private MovementRepository movementRepository;
    @Mock private MovementApprovalRecordRepository recordRepository;
    @Mock private ApproverChainResolver approverChainResolver;
    @Mock private CurrentUserService currentUserService;
    @Mock private AppNotificationService notificationService;
    @Mock private Email email;
    @Mock private AuditService auditService;

    @InjectMocks
    private MovementApprovalService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester", null,
                        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    /** No approver resolves, which is the only situation in which the bypass is offered. */
    private Movement movementWithNoApprover() {
        when(approverChainResolver.resolveInitiatorChainDetailed(any()))
                .thenReturn(new ApproverChainResolver.InitiatorChain(
                        List.of(), "The initiator's title reports to nothing."));
        Movement movement = new Movement();
        movement.setId(1L);
        return movement;
    }

    @Test
    void aUserWhoMayOnlyCreateMovementsCannotAuthoriseTheirOwnBypass() {
        Movement movement = movementWithNoApprover();
        authenticateWith(Constants.CREATE_MOVEMENT, Constants.DISPATCH_MOVEMENT);

        assertThatThrownBy(() -> service.startApproval(movement, true, "No one to approve this"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(Constants.APPROVE_MOVEMENT);
    }

    /** The refusal must leave nothing behind — no INITIATED movement, no BYPASSED trail entry. */
    @Test
    void aRefusedBypassCommitsNothing() {
        Movement movement = movementWithNoApprover();
        authenticateWith(Constants.CREATE_MOVEMENT);

        assertThatThrownBy(() -> service.startApproval(movement, true, "No one to approve this"))
                .isInstanceOf(AccessDeniedException.class);

        verify(movementRepository, never()).save(any());
        verify(recordRepository, never()).save(any());
        assertThat(movement.isApprovalBypassed()).isFalse();
    }

    @Test
    void anApproverMayAuthoriseTheBypass() {
        Movement movement = movementWithNoApprover();
        when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        authenticateWith(Constants.APPROVE_MOVEMENT);

        service.startApproval(movement, true, "Branch manager post is vacant");

        assertThat(movement.isApprovalBypassed()).isTrue();
        assertThat(movement.getStatus()).isEqualTo(MovementStatus.INITIATED);
    }

    /**
     * An unauthenticated context must not be read as permission. The check asks the security
     * context, and a null authentication is the state a background or unauthenticated call arrives
     * in — treating that as "no restriction" is the failure mode this whole pass exists to close.
     */
    @Test
    void anUnauthenticatedCallerCannotBypass() {
        Movement movement = movementWithNoApprover();
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.startApproval(movement, true, "No one to approve this"))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * The bypass is the only thing gated here. Someone with no movement permissions at all still
     * gets the ordinary "no approver could be resolved" fault when they have not asked to proceed —
     * that is a configuration problem, and it must not be reported as a permissions one.
     */
    @Test
    void theOrdinaryNoApproverFaultIsUnchangedForEveryoneElse() {
        Movement movement = movementWithNoApprover();
        authenticateWith(Constants.CREATE_MOVEMENT);

        assertThatThrownBy(() -> service.startApproval(movement, false, null))
                .isNotInstanceOf(AccessDeniedException.class);
    }
}
