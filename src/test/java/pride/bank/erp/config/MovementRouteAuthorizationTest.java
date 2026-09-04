/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Pins which authority each movement and consignment action answers to.
 *
 * <p>Every endpoint in this module fell through to {@code .anyRequest().authenticated()} until
 * 2026-08-27: any signed-in user could raise a transfer, send stock out of a building, receive it at
 * the other end, or cancel a journey already on the road. The guards are {@code @PreAuthorize}
 * annotations rather than URL matchers, so what this test defends is not ordering but the
 * annotations simply being there — a new endpoint added without one is invisible until something
 * asks.
 *
 * <p>The separations worth stating out loud, because each is a thing someone will later be tempted
 * to collapse:
 * <ul>
 *   <li><b>Dispatch is not create.</b> Raising a transfer and physically handing custody to a
 *       courier are different duties.</li>
 *   <li><b>Receive is not dispatch.</b> The person who sends is not the person who signs for it.</li>
 *   <li><b>Cancel is neither.</b> It stops a journey already in flight.</li>
 *   <li><b>Approve is not read.</b> Seeing the queue is not being allowed to decide.</li>
 * </ul>
 *
 * <p>Each case asserts 403 for an authority that must not open the route, and "anything but 403" for
 * the one that must — see {@code AssetRouteAuthorizationTest#assertNotForbidden} for why the second
 * half is deliberately loose.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MovementRouteAuthorizationTest {

    private static final String API = "/api/v1";

    @Autowired
    private MockMvc mockMvc;

    /**
     * A fragment of {@code MovementService#listMovements}'s own refusal.
     *
     * <p>That method throws {@code AccessDeniedException} when the caller's account has no branch,
     * which renders as a 403 through the same handler as a permission failure. {@code @WithMockUser}
     * supplies no branch, so the listing endpoints always produce it — and a test that only counted
     * 403s would pass just as happily with every {@code @PreAuthorize} deleted. Telling the two
     * apart is the difference between this file guarding something and merely appearing to.
     */
    private static final String BRANCH_REFUSAL = "not attached to a branch";

    /**
     * A movement id that cannot exist.
     *
     * <p>These cases are about the filter chain — which authority opens which route — and nothing
     * else. Since the record-level scope check landed, a handler reached with a real id can refuse
     * with its own 403 because the movement touches no branch of the caller's, and by status alone
     * that is indistinguishable from the route rule refusing. Naming an absent movement makes the
     * handler fail on the lookup instead, so a 403 here can only have come from the rule under test.
     *
     * <p>Whether a caller may act on a <em>particular</em> movement is
     * {@code MultiBranchReachTest}'s subject.
     */
    private static final String MISSING_MOVEMENT = "999999999";

    private String bodyOf(RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getContentAsString();
    }

    /** Refused, and refused by the permission rule rather than by a business rule behind it. */
    private void assertForbidden(RequestBuilder request) throws Exception {
        var response = mockMvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as("expected the route to be refused").isEqualTo(403);
        assertThat(response.getContentAsString())
                .as("expected a permission refusal, not a business one — the guard may be missing")
                .doesNotContain(BRANCH_REFUSAL);
    }

    /**
     * Admitted by the permission rule.
     *
     * <p>Three things count as admitted, because authorization refusals are rendered as a 403 by the
     * access-denied handler and never reach the controller: any non-403 status; an exception thrown
     * inside the handler (several read the caller off the principal and cast it to the application's
     * own {@code User}, which {@code @WithMockUser} does not supply); and a 403 carrying the branch
     * refusal above, which is raised by the service after the guard has already let the call through.
     */
    private void assertNotForbidden(RequestBuilder request) throws Exception {
        int status;
        String body;
        try {
            var response = mockMvc.perform(request).andReturn().getResponse();
            status = response.getStatus();
            body = response.getContentAsString();
        } catch (Exception handlerFailed) {
            return;
        }
        if (status == 403 && body.contains(BRANCH_REFUSAL)) return;
        assertThat(status).as("expected authorization to let this through").isNotEqualTo(403);
    }

    private static RequestBuilder json(String path) {
        return post(API + path).contentType("application/json").content("{}");
    }

    // ── Nothing is open to a bare authenticated user any more ────────────────

    @Test
    @WithMockUser(authorities = "READ_REQUEST")
    void listingMovementsIsRefusedWithoutTheMovementReadPermission() throws Exception {
        assertForbidden(get(API + "/movements"));
    }

    @Test
    @WithMockUser(authorities = "READ_ASSET")
    void readAssetNoLongerOpensTheMovementsList() throws Exception {
        assertForbidden(get(API + "/movements"));
    }

    @Test
    @WithMockUser(authorities = "READ_MOVEMENT")
    void listingMovementsIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(get(API + "/movements"));
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "READ_MOVEMENT")
    void raisingAMovementIsRefusedToSomeoneWhoMayOnlyLook() throws Exception {
        assertForbidden(json("/movements"));
    }

    @Test
    @WithMockUser(authorities = "CREATE_MOVEMENT")
    void raisingAMovementIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(json("/movements"));
    }

    @Test
    @WithMockUser(authorities = "CREATE_ASSET")
    void createAssetNoLongerRaisesRepairTransfers() throws Exception {
        assertForbidden(json("/movements/repair-transfer"));
    }

    // ── Dispatch is not create ───────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "CREATE_MOVEMENT")
    void dispatchIsRefusedToSomeoneWhoMayOnlyRaiseMovements() throws Exception {
        assertForbidden(json("/movements/" + MISSING_MOVEMENT + "/dispatch"));
    }

    @Test
    @WithMockUser(authorities = "DISPATCH_MOVEMENT")
    void dispatchIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(json("/movements/" + MISSING_MOVEMENT + "/dispatch"));
    }

    @Test
    @WithMockUser(authorities = "CREATE_MOVEMENT")
    void markingInTransitIsRefusedToSomeoneWhoMayOnlyRaiseMovements() throws Exception {
        assertForbidden(json("/movements/" + MISSING_MOVEMENT + "/in-transit"));
    }

    // ── Receive is not dispatch ──────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "DISPATCH_MOVEMENT")
    void receivingIsRefusedToTheDispatcher() throws Exception {
        assertForbidden(json("/movements/" + MISSING_MOVEMENT + "/receive"));
    }

    @Test
    @WithMockUser(authorities = "RECEIVE_MOVEMENT")
    void receivingIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(json("/movements/" + MISSING_MOVEMENT + "/receive"));
    }

    /** Complete lands the goods, so it must not be reachable below the authority to receive. */
    @Test
    @WithMockUser(authorities = {"CREATE_MOVEMENT", "DISPATCH_MOVEMENT"})
    void completingAMovementIsRefusedWithoutTheReceivePermission() throws Exception {
        assertForbidden(json("/movements/" + MISSING_MOVEMENT + "/complete"));
    }

    // ── Cancel ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"CREATE_MOVEMENT", "DISPATCH_MOVEMENT", "RECEIVE_MOVEMENT"})
    void cancellingIsRefusedToSomeoneWithEveryOtherMovementPermission() throws Exception {
        assertForbidden(json("/movements/" + MISSING_MOVEMENT + "/cancel"));
    }

    @Test
    @WithMockUser(authorities = "CANCEL_MOVEMENT")
    void cancellingIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(json("/movements/" + MISSING_MOVEMENT + "/cancel"));
    }

    // ── Approve is not read ──────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "READ_MOVEMENT")
    void approvingIsRefusedToSomeoneWhoMayOnlyLook() throws Exception {
        assertForbidden(json("/movements/1/approve"));
    }

    @Test
    @WithMockUser(authorities = "READ_MOVEMENT")
    void rejectingIsRefusedToSomeoneWhoMayOnlyLook() throws Exception {
        assertForbidden(json("/movements/1/reject"));
    }

    @Test
    @WithMockUser(authorities = "APPROVE_MOVEMENT")
    void approvingIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(json("/movements/1/approve"));
    }

    /** The approver inbox is a queue of decisions, not a listing. */
    @Test
    @WithMockUser(authorities = "READ_MOVEMENT")
    void theApprovalInboxIsRefusedWithoutTheApprovePermission() throws Exception {
        assertForbidden(get(API + "/movements/pending-approval/1"));
    }

    // ── Consignments answer to the same six ──────────────────────────────────

    @Test
    @WithMockUser(authorities = "READ_ASSET")
    void listingConsignmentsIsRefusedWithoutTheMovementReadPermission() throws Exception {
        assertForbidden(get(API + "/consignments"));
    }

    @Test
    @WithMockUser(authorities = "READ_MOVEMENT")
    void listingConsignmentsIsAllowedByTheMovementReadPermission() throws Exception {
        assertNotForbidden(get(API + "/consignments"));
    }

    @Test
    @WithMockUser(authorities = "CREATE_MOVEMENT")
    void dispatchingAConsignmentIsRefusedToSomeoneWhoMayOnlyAssembleOne() throws Exception {
        assertForbidden(json("/consignments/1/dispatch"));
    }

    @Test
    @WithMockUser(authorities = "DISPATCH_MOVEMENT")
    void markingAConsignmentArrivedIsRefusedToTheDispatcher() throws Exception {
        assertForbidden(json("/consignments/1/arrive"));
    }

    @Test
    @WithMockUser(authorities = "RECEIVE_MOVEMENT")
    void handingAConsignmentOverIsAllowedByTheReceivePermission() throws Exception {
        assertNotForbidden(json("/consignments/1/receive"));
    }

    @Test
    @WithMockUser(authorities = "READ_MOVEMENT")
    void cancellingAConsignmentIsRefusedToSomeoneWhoMayOnlyLook() throws Exception {
        assertForbidden(json("/consignments/1/cancel"));
    }

    @Test
    @WithMockUser(authorities = "READ_MOVEMENT")
    void loadingAMovementOntoAConsignmentIsRefusedToSomeoneWhoMayOnlyLook() throws Exception {
        assertForbidden(json("/consignments/1/movements/1"));
    }

    @Test
    @WithMockUser(authorities = "READ_MOVEMENT")
    void unloadingAMovementIsRefusedToSomeoneWhoMayOnlyLook() throws Exception {
        assertForbidden(delete(API + "/consignments/1/movements/1"));
    }
}
