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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import pride.bank.erp.advice.AccessDenied;

/**
 * Pins which authority each asset action answers to.
 *
 * <p>Two things are being guarded, and both fail silently.
 *
 * <p>The first is <b>matcher order</b>. Spring evaluates {@code requestMatchers} in declaration
 * order and the first match wins, so the specific rules for reassign, repair, receive-into-store and
 * bulk import only mean anything while they sit above the generic {@code POST /assets/**} and
 * {@code PUT /assets/**} rules. Move one below and it stops being consulted — the route still
 * works, it simply answers to the wrong permission, and nothing anywhere reports it.
 *
 * <p>The second is <b>coverage</b>. Disposal lives under {@code /movements} and the register extract
 * under {@code /export}; neither matched any rule, so both fell through to
 * {@code .anyRequest().authenticated()} and any signed-in user could write an asset off or walk out
 * with the whole register.
 *
 * <p>Each case asserts a 403 for the permission that must NOT open the route, and "anything but 403"
 * for the one that must. The second half is deliberately loose: what the handler then does with an
 * absent asset or a missing body is that handler's business, not authorization's.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssetRouteAuthorizationTest {
    /*
     * The status a refusal arrives under, read from the application rather than written as 403.
     *
     * The firewall in front of this service blocks 403, so refusals travel as AccessDenied.STATUS
     * instead. Hard-coding the number here would mean a firewall rule change breaks nine tests in
     * four files with no hint that they are all the same fact; reading it from the source keeps the
     * assertion about *being refused* rather than about a number.
     */


    private static final String API = "/api/v1";

    /**
     * An asset id that cannot exist.
     *
     * <p>These cases are about the filter chain — which authority opens which route — and nothing
     * else. Since the record-level scope check landed, a handler reached with a real id can refuse
     * with its own 403 because the asset belongs to another branch, and by status alone that is
     * indistinguishable from the route rule refusing. Naming an absent asset makes the handler fail
     * on the lookup instead, so a 403 here can only have come from the rule under test.
     *
     * <p>Whether the caller may touch a <em>particular</em> asset is
     * {@code AccessScopeServiceTest}'s subject.
     */
    private static final String MISSING_ASSET = "999999999";

    @Autowired
    private MockMvc mockMvc;

    private void assertForbidden(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        assertThat(mockMvc.perform(request).andReturn().getResponse().getStatus())
                .as("expected the route to be refused")
                .isEqualTo(AccessDenied.STATUS.value());
    }

    /**
     * Asserts only that the filter chain admitted the request.
     *
     * <p>A handler that then throws counts as admitted: authorization refusals are rendered as a 403
     * response by the access-denied handler and never reach the controller, so an exception raised
     * inside one is proof the request got past the rule under test. Several of these handlers read
     * the caller off the principal and cast it to the application's own {@code User}, which
     * {@code @WithMockUser} does not supply — that is a fact about the fixture, not about the
     * permission, and pinning it would make the test fail for reasons it is not about.
     */
    private void assertNotForbidden(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        int status;
        try {
            status = mockMvc.perform(request).andReturn().getResponse().getStatus();
        } catch (Exception handlerFailed) {
            return;
        }
        assertThat(status)
                .as("expected authorization to let this through")
                .isNotEqualTo(AccessDenied.STATUS.value());
    }

    // ── Reassign — must not answer to CREATE_ASSET ───────────────────────────

    @Test
    @WithMockUser(authorities = "CREATE_ASSET")
    void reassignIsRefusedToSomeoneWhoCanOnlyRegisterAssets() throws Exception {
        assertForbidden(post(API + "/assets/reassign/" + MISSING_ASSET).contentType("application/json").content("{}"));
    }

    @Test
    @WithMockUser(authorities = "REASSIGN_ASSET")
    void reassignIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(post(API + "/assets/reassign/" + MISSING_ASSET).contentType("application/json").content("{}"));
    }

    // ── Repair ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "CREATE_ASSET")
    void bookingARepairIsRefusedToSomeoneWhoCanOnlyRegisterAssets() throws Exception {
        assertForbidden(post(API + "/assets/repairs/" + MISSING_ASSET));
    }

    @Test
    @WithMockUser(authorities = "UPDATE_ASSET")
    void closingARepairIsRefusedToSomeoneWhoCanOnlyEditAssets() throws Exception {
        assertForbidden(put(API + "/assets/repairs/" + MISSING_ASSET));
    }

    /**
     * Reading the repair history is a different duty from performing the repair — and now from
     * reading the asset.
     *
     * <p>It answered to {@code READ_ASSET} until the trail was split out. Seeing what an asset is
     * and seeing its maintenance record are separate disclosures, so they are separate permissions;
     * existing asset readers were backfilled once, at the split, so nobody lost the tab.
     */
    @Test
    @WithMockUser(authorities = "READ_REPAIR_HISTORY")
    void repairHistoryIsReadableWithItsOwnPermission() throws Exception {
        assertNotForbidden(get(API + "/assets/repairs/" + MISSING_ASSET));
    }

    @Test
    @WithMockUser(authorities = "READ_ASSET")
    void repairHistoryIsNoLongerReadableOnReadAssetAlone() throws Exception {
        assertForbidden(get(API + "/assets/repairs/" + MISSING_ASSET));
    }

    /** And the custody trail answers to its own permission too. */
    @Test
    @WithMockUser(authorities = "READ_ASSIGNMENT_HISTORY")
    void assignmentHistoryIsReadableWithItsOwnPermission() throws Exception {
        assertNotForbidden(get(API + "/assignment-history"));
    }

    @Test
    @WithMockUser(authorities = "READ_ASSET")
    void assignmentHistoryIsNoLongerReadableOnReadAssetAlone() throws Exception {
        assertForbidden(get(API + "/assignment-history"));
    }

    // ── Receive into store — must not answer to UPDATE_ASSET ─────────────────

    @Test
    @WithMockUser(authorities = "UPDATE_ASSET")
    void receivingIntoStoreIsRefusedToSomeoneWhoCanOnlyEditAssets() throws Exception {
        assertForbidden(put(API + "/assets/store/" + MISSING_ASSET));
    }

    @Test
    @WithMockUser(authorities = "RECEIVE_ASSET_IN_STORE")
    void receivingIntoStoreIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(put(API + "/assets/store/" + MISSING_ASSET));
    }

    // ── Bulk import ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "CREATE_ASSET")
    void bulkImportIsRefusedToSomeoneWhoCanOnlyRegisterAssetsOneAtATime() throws Exception {
        assertForbidden(post(API + "/assets/bulk-insert"));
    }

    @Test
    @WithMockUser(authorities = "IMPORT_ASSET")
    void bulkImportIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(post(API + "/assets/bulk-insert"));
    }

    // ── Disposal — lives under /movements, so it used to match nothing ───────

    @Test
    @WithMockUser(authorities = {"READ_ASSET", "UPDATE_ASSET", "CREATE_ASSET"})
    void disposalIsRefusedToSomeoneWithEveryOtherAssetPermission() throws Exception {
        assertForbidden(post(API + "/movements/disposal").contentType("application/json").content("{}"));
    }

    @Test
    @WithMockUser(authorities = "DISPOSE_ASSET")
    void disposalIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(post(API + "/movements/disposal").contentType("application/json").content("{}"));
    }

    @Test
    @WithMockUser(authorities = "READ_ASSET")
    void theDisposalPreviewIsRefusedWithoutTheDisposalPermission() throws Exception {
        assertForbidden(get(API + "/movements/disposal/preview").param("assetId", "1"));
    }

    // ── Export — the whole register in one unpaginated file ──────────────────

    @Test
    @WithMockUser(authorities = "READ_ASSET")
    void exportingTheRegisterIsRefusedToSomeoneWhoMayOnlyReadIt() throws Exception {
        assertForbidden(get(API + "/export/assets").param("assetTypeId", "1"));
    }

    @Test
    @WithMockUser(authorities = "EXPORT_ASSET")
    void exportingTheRegisterIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(get(API + "/export/assets").param("assetTypeId", "1"));
    }

    // ── Dashboard statistics stand on the dashboard axis ─────────────────────

    /**
     * The counts behind "Assets by Category" and "Asset Condition". Granting DASH_VIEW_ASSETS is
     * meant to be enough to see the estate summarised without also opening the full register; while
     * this matched the generic GET rule it demanded READ_ASSET, and the dashboard rendered a widget
     * its own endpoint then refused.
     */
    @Test
    @WithMockUser(authorities = "DASH_VIEW_ASSETS")
    void assetStatisticsAreReachableOnTheDashboardPermissionAlone() throws Exception {
        assertNotForbidden(get(API + "/assets/statistics"));
    }

    @Test
    @WithMockUser(authorities = "READ_REQUEST")
    void assetStatisticsAreStillRefusedToSomeoneWithNeitherAssetAuthority() throws Exception {
        assertForbidden(get(API + "/assets/statistics"));
    }
}
