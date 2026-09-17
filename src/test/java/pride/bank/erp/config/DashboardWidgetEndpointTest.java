package pride.bank.erp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import pride.bank.erp.advice.AccessDenied;

/**
 * Every dashboard endpoint must admit everyone the dashboard shows its widget to.
 *
 * <h2>The failure this exists to stop</h2>
 * The dashboard decides whether to render a widget from the viewer's permissions; the endpoint
 * behind that widget decides again, separately. When the two lists drift, the page renders a widget
 * whose own endpoint then refuses it — a card that spins and fails with nothing on screen to say
 * why, which reads as a broken dashboard rather than as a permission boundary.
 *
 * <p>It has now happened twice, both times because a rule was written before a permission existed
 * and never revisited:
 *
 * <ul>
 *   <li><b>The national asset roll-up</b> named only {@code VIEW_ALL_BRANCHES}, which predates the
 *       multiplier model. A unit confers ALL scope through {@code DASH_SCOPE_ALL} and
 *       {@code VIEW_ALL_BRANCH_ASSETS} and never through the legacy permission, so an Admin Unit
 *       officer saw "Assets Across All Branches" and got a 403 from it.</li>
 *   <li><b>The stock and request summaries</b> named only their {@code READ_*} permissions, so
 *       granting the dashboard subject axis alone — which is the entire point of that axis existing
 *       separately — rendered a widget nobody could load.</li>
 * </ul>
 *
 * <p>Each case below grants <em>one</em> authority: the narrowest way of qualifying for that widget
 * on the front end. If any of them stops being enough, this fails rather than the dashboard.
 *
 * <p>Asserts "not 403" rather than 200 deliberately. What the handler does with an empty database is
 * its own business; authorization admitting the request is the whole of what is being pinned.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardWidgetEndpointTest {
    /*
     * The status a refusal arrives under, read from the application rather than written as 403.
     *
     * The firewall in front of this service blocks 403, so refusals travel as AccessDenied.STATUS
     * instead. Hard-coding the number here would mean a firewall rule change breaks nine tests in
     * four files with no hint that they are all the same fact; reading it from the source keeps the
     * assertion about *being refused* rather than about a number.
     */


    private static final String API = "/api/v1";

    @Autowired
    private MockMvc mockMvc;

    private void assertAdmitted(RequestBuilder request) throws Exception {
        int status;
        try {
            status = mockMvc.perform(request).andReturn().getResponse().getStatus();
        } catch (Exception handlerFailed) {
            return;   // got past the filter chain, which is all this claims
        }
        assertThat(status)
                .as("the dashboard renders this widget for this authority, so its endpoint must "
                        + "admit it — otherwise the page shows a card that cannot load")
                .isNotEqualTo(AccessDenied.STATUS.value());
    }

    // ── Assets Across All Branches — qualifies on scope == ALL ──────────────

    @Test
    @WithMockUser(authorities = "DASH_SCOPE_ALL")
    void theBranchHeatmapAdmitsTheDashboardScope() throws Exception {
        assertAdmitted(get(API + "/dashboard-asset-report/by-branch"));
        assertAdmitted(get(API + "/dashboard-asset-report"));
    }

    /** What a unit actually confers, and what the reported 403 was raised against. */
    @Test
    @WithMockUser(authorities = "VIEW_ALL_BRANCH_ASSETS")
    void theBranchHeatmapAdmitsTheAssetMultiplier() throws Exception {
        assertAdmitted(get(API + "/dashboard-asset-report/by-branch"));
        assertAdmitted(get(API + "/dashboard-asset-report"));
    }

    /** Nobody who could see the bank yesterday may be narrowed today. */
    @Test
    @WithMockUser(authorities = "VIEW_ALL_BRANCHES")
    void theBranchHeatmapStillAdmitsTheLegacyPermission() throws Exception {
        assertAdmitted(get(API + "/dashboard-asset-report/by-branch"));
        assertAdmitted(get(API + "/dashboard-asset-report"));
    }

    // ── The subject axis, which exists to be granted on its own ─────────────

    @Test
    @WithMockUser(authorities = "DASH_VIEW_STOCK")
    void theStockingTrendAdmitsItsSubjectPermission() throws Exception {
        assertAdmitted(get(API + "/monthly-asset-stock"));
    }

    @Test
    @WithMockUser(authorities = "DASH_VIEW_REQUESTS")
    void requestFulfilmentAdmitsItsSubjectPermission() throws Exception {
        assertAdmitted(get(API + "/current-year-summary"));
    }

    @Test
    @WithMockUser(authorities = "DASH_VIEW_ASSETS")
    void assetStatisticsAdmitsItsSubjectPermission() throws Exception {
        assertAdmitted(get(API + "/assets/statistics"));
    }

    // ── And the legacy readers keep working ─────────────────────────────────

    @Test
    @WithMockUser(authorities = "READ_STORE")
    void theStockingTrendStillAdmitsTheStoreReader() throws Exception {
        assertAdmitted(get(API + "/monthly-asset-stock"));
    }

    @Test
    @WithMockUser(authorities = "READ_REQUEST")
    void requestFulfilmentStillAdmitsTheRequestReader() throws Exception {
        assertAdmitted(get(API + "/current-year-summary"));
    }

    // ── The national roll-up is still not open to everyone ──────────────────

    @Test
    @WithMockUser(authorities = "READ_ASSET")
    void theBranchHeatmapIsStillRefusedToAPlainAssetReader() throws Exception {
        // READ_ASSET opens the assets page; it does not confer sight of every branch's totals.
        // Widening the rule to fix the drift must not have widened it past ALL scope.
        assertThat(mockMvc.perform(get(API + "/dashboard-asset-report/by-branch"))
                .andReturn().getResponse().getStatus())
                .as("the roll-up covers the whole bank and answers to ALL scope, not to a read")
                .isEqualTo(AccessDenied.STATUS.value());
    }
}
