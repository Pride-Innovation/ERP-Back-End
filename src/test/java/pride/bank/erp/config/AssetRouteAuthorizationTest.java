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

    private static final String API = "/api/v1";

    @Autowired
    private MockMvc mockMvc;

    private void assertForbidden(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        assertThat(mockMvc.perform(request).andReturn().getResponse().getStatus())
                .as("expected the route to be refused")
                .isEqualTo(403);
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
                .isNotEqualTo(403);
    }

    // ── Reassign — must not answer to CREATE_ASSET ───────────────────────────

    @Test
    @WithMockUser(authorities = "CREATE_ASSET")
    void reassignIsRefusedToSomeoneWhoCanOnlyRegisterAssets() throws Exception {
        assertForbidden(post(API + "/assets/reassign/1").contentType("application/json").content("{}"));
    }

    @Test
    @WithMockUser(authorities = "REASSIGN_ASSET")
    void reassignIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(post(API + "/assets/reassign/1").contentType("application/json").content("{}"));
    }

    // ── Repair ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "CREATE_ASSET")
    void bookingARepairIsRefusedToSomeoneWhoCanOnlyRegisterAssets() throws Exception {
        assertForbidden(post(API + "/assets/repairs/1"));
    }

    @Test
    @WithMockUser(authorities = "UPDATE_ASSET")
    void closingARepairIsRefusedToSomeoneWhoCanOnlyEditAssets() throws Exception {
        assertForbidden(put(API + "/assets/repairs/1"));
    }

    /** Reading the repair history is a different duty from performing the repair. */
    @Test
    @WithMockUser(authorities = "READ_ASSET")
    void repairHistoryIsReadableWithReadAsset() throws Exception {
        assertNotForbidden(get(API + "/assets/repairs/1"));
    }

    // ── Receive into store — must not answer to UPDATE_ASSET ─────────────────

    @Test
    @WithMockUser(authorities = "UPDATE_ASSET")
    void receivingIntoStoreIsRefusedToSomeoneWhoCanOnlyEditAssets() throws Exception {
        assertForbidden(put(API + "/assets/store/1"));
    }

    @Test
    @WithMockUser(authorities = "RECEIVE_ASSET_IN_STORE")
    void receivingIntoStoreIsAllowedByItsOwnPermission() throws Exception {
        assertNotForbidden(put(API + "/assets/store/1"));
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
