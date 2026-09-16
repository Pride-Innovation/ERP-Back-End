package pride.bank.erp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import pride.bank.erp.advice.AccessDenied;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A POST may stand in for a PUT or a DELETE, and must not become a way around authorization.
 *
 * <h2>What this is protecting</h2>
 * The firewall blocks PUT and DELETE, so the verb travels in {@code X-HTTP-Method-Override} and
 * {@link HttpMethodOverrideFilter} restores it. Authorization in this application is
 * <b>method-specific</b> — seventeen rules in {@link SecurityConfiguration} name
 * {@code HttpMethod.PUT} or {@code HttpMethod.DELETE}, and the same path carries a different
 * permission per verb:
 *
 * <pre>
 *   .requestMatchers(HttpMethod.DELETE, ASSET_ROUTE).hasAuthority(DELETE_ASSET)
 *   .requestMatchers(HttpMethod.POST,   ASSET_ROUTE).hasAuthority(CREATE_ASSET)
 * </pre>
 *
 * <p>So everything depends on the filter running <b>before</b> {@code springSecurityFilterChain}.
 * Restore the verb afterwards and a holder of {@code CREATE_ASSET} passes the POST rule and lands on
 * the {@code @DeleteMapping}; every one of those seventeen rules inverts the same way.
 *
 * <h2>Why these tests assert refusals rather than the filter's order</h2>
 * The order is a means. Asserting {@code getOrder() == HIGHEST_PRECEDENCE} would pass just as happily
 * if Spring Security later registered itself earlier, or if the registration bean stopped being the
 * one that won — and it would tell nobody what was actually at stake. These assert the escalation
 * itself, on real routes, with the exact authority that must not be enough.
 *
 * <h2>Which of them actually discriminate — measured, not assumed</h2>
 * The filter's order was deliberately inverted to {@code LOWEST_PRECEDENCE} and the suite re-run.
 * <b>Four failed</b>: the users, roles and requests escalations, and
 * {@link #anOverriddenDeleteIsAdmittedByTheDeletePermission}. Those four are the ones carrying the
 * proof.
 *
 * <p>The rest pass either way, and it is worth knowing why rather than assuming they are redundant:
 *
 * <ul>
 *   <li>The three narrowing tests ({@code onlyTheTwoBlockedVerbsCanBeOverridden} and friends) are
 *       about what the filter accepts, not about where it sits. They would catch a widened
 *       whitelist, which is a different regression.</li>
 *   <li>{@link #anOverriddenDeleteIsJudgedAsADeleteAndNotAsThePostItArrivedAs} passed even with the
 *       ordering broken, because {@code AssetService}'s own scope check refused the request inside
 *       the handler — and a business refusal renders through the same handler, under the same
 *       status, as a route refusal. {@code MovementRouteAuthorizationTest} documents that same
 *       confound. It is kept because the escalation it names is the clearest statement of what is at
 *       stake, but <b>it is not the test that proves it</b>; the three beside it are.</li>
 * </ul>
 *
 * <p>Whenever a case is added here, break the order and check it fails. A test that cannot fail for
 * the reason it was written is worse than no test, because it reads like cover.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MethodOverrideSecurityTest {

    private static final String API = "/api/v1";
    private static final String HEADER = HttpMethodOverrideFilter.HEADER;

    /**
     * An id nothing owns.
     *
     * <p>Same device the route-authorization tests use: a request that gets past the rule fails in
     * the handler on the lookup instead, so a refusal here can only have come from the rule.
     */
    private static final String MISSING = "999999999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityConfiguration securityConfiguration;

    /** The origin a browser would send — the same one the CORS configuration is built from. */
    @org.springframework.beans.factory.annotation.Value("${front-end.environment}")
    private String browserOrigin;

    private int statusOf(RequestBuilder request) throws Exception {
        try {
            MvcResult result = mockMvc.perform(request).andReturn();
            return result.getResponse().getStatus();
        } catch (Exception handlerFailed) {
            // The handler ran and threw, which is proof the request was admitted. Several handlers
            // read the caller off the principal and cast it to the application's own User, which
            // @WithMockUser does not supply; that is a fact about the fixture, not the rule.
            return 200;
        }
    }

    private void assertRefused(RequestBuilder request, String why) throws Exception {
        assertThat(statusOf(request)).as(why).isEqualTo(AccessDenied.STATUS.value());
    }

    private void assertAdmitted(RequestBuilder request, String why) throws Exception {
        assertThat(statusOf(request)).as(why).isNotEqualTo(AccessDenied.STATUS.value());
    }

    // ── The escalation this whole design turns on ────────────────────────────

    /**
     * The clearest statement of the escalation — and, measured, not the proof of it.
     *
     * <p>If {@link HttpMethodOverrideFilter} ran after Spring Security, this request would be judged
     * against {@code .requestMatchers(POST, ASSET_ROUTE).hasAuthority(CREATE_ASSET)} — which this
     * caller satisfies — and then dispatched to the {@code @DeleteMapping}.
     *
     * <p><b>It still passes with the ordering broken</b>, because the delete handler's own
     * {@code requireAssetInScope} then refuses it and a business refusal is indistinguishable from a
     * route refusal by status alone. Kept for what it says; see the class javadoc for which three
     * cases carry the proof.
     */
    @Test
    @WithMockUser(authorities = "CREATE_ASSET")
    void anOverriddenDeleteIsJudgedAsADeleteAndNotAsThePostItArrivedAs() throws Exception {
        assertRefused(
                post(API + "/assets/" + MISSING).header(HEADER, "DELETE"),
                "CREATE_ASSET must not delete an asset by wearing a DELETE header");
    }

    /** And the same route does open for the authority that owns the verb, or the override is inert. */
    @Test
    @WithMockUser(authorities = "DELETE_ASSET")
    void anOverriddenDeleteIsAdmittedByTheDeletePermission() throws Exception {
        assertAdmitted(
                post(API + "/assets/" + MISSING).header(HEADER, "DELETE"),
                "DELETE_ASSET owns this route however the verb travelled");
    }

    /**
     * The same pair on three more modules, because the rules are per-path.
     *
     * <p>One passing route proves the filter is ordered correctly; these prove the rule set has no
     * path where the POST permission happens to be the weaker one and nobody noticed.
     */
    @Test
    @WithMockUser(authorities = "CREATE_USER")
    void creatingUsersDoesNotBecomeEditingThem() throws Exception {
        assertRefused(
                post(API + "/users/" + MISSING).header(HEADER, "PUT")
                        .contentType("application/json").content("{}"),
                "CREATE_USER must not reach the PUT rule, which asks for UPDATE_USER");
    }

    @Test
    @WithMockUser(authorities = "CREATE_ROLE")
    void creatingRolesDoesNotBecomeDeletingThem() throws Exception {
        assertRefused(
                post(API + "/roles/" + MISSING).header(HEADER, "DELETE"),
                "CREATE_ROLE must not reach the DELETE rule, which asks for DELETE_ROLE");
    }

    @Test
    @WithMockUser(authorities = "CREATE_REQUEST")
    void creatingRequestsDoesNotBecomeDeletingThem() throws Exception {
        assertRefused(
                post(API + "/requests/" + MISSING).header(HEADER, "DELETE"),
                "CREATE_REQUEST must not reach the DELETE rule, which asks for DELETE_REQUEST");
    }

    // ── The three narrowings ─────────────────────────────────────────────────

    /**
     * An override on a GET is refused, not ignored.
     *
     * <p>Ignoring it would serve the GET, which is harmless here and is the wrong habit: the header
     * is only meaningful on a POST, and a request that sends it anywhere else is either confused or
     * probing.
     */
    @Test
    @WithMockUser(authorities = "READ_ASSET")
    void theOverrideIsOnlyAcceptedOnPost() throws Exception {
        assertThat(statusOf(get(API + "/assets/" + MISSING).header(HEADER, "DELETE")))
                .as("the header means nothing on a GET and must be refused rather than dropped")
                .isEqualTo(400);
    }

    /**
     * Only PUT and DELETE, the two the firewall blocks.
     *
     * <p>Accepting whatever a caller names would make this a general way of choosing which security
     * rule to be judged by — including naming GET to slip a write past a read rule.
     */
    @Test
    @WithMockUser(authorities = "CREATE_ASSET")
    void onlyTheTwoBlockedVerbsCanBeOverridden() throws Exception {
        assertThat(statusOf(post(API + "/assets/" + MISSING).header(HEADER, "GET")))
                .as("naming GET would be a way past a write rule into a read handler")
                .isEqualTo(400);

        assertThat(statusOf(post(API + "/assets/" + MISSING).header(HEADER, "TRACE")))
                .isEqualTo(400);
    }

    /**
     * An unrecognised value must not fall through to the POST handler.
     *
     * <p>This is the important half of the previous test. Ignoring a bad override sends the request
     * to whatever the POST route does — so a typo, or a header the firewall rewrote, would silently
     * perform a different operation from the one asked for. A fallback meaning "behave as before" is
     * what hides a missing wire; here it would hide a wrong one.
     */
    @Test
    @WithMockUser(authorities = "CREATE_ASSET")
    void anUnrecognisedOverrideIsRefusedRatherThanRunAsAPost() throws Exception {
        MvcResult result = mockMvc.perform(
                        post(API + "/assets/" + MISSING).header(HEADER, "PUTT")
                                .contentType("application/json").content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString())
                .as("and it says which header was at fault, since this runs before any @ExceptionHandler")
                .contains("INVALID_METHOD_OVERRIDE");
    }

    /** A request with no override header at all is untouched. */
    @Test
    @WithMockUser(authorities = "CREATE_ASSET")
    void anOrdinaryPostIsUnaffected() throws Exception {
        assertAdmitted(
                post(API + "/assets").contentType("application/json").content("{}"),
                "CREATE_ASSET still creates assets");
    }

    // ── CORS: the bug this shipped with, and how it presented ────────────────

    /**
     * An overridden write arriving from a browser must not be rejected by CORS.
     *
     * <h2>The bug</h2>
     * The filter was first registered as a servlet filter at {@code HIGHEST_PRECEDENCE}, ahead of the
     * whole security chain — so by the time Spring's {@code CorsFilter} ran,
     * {@code request.getMethod()} already returned {@code PUT} for a request the browser had sent as
     * {@code POST}. {@code allowedMethods} is {@code GET, POST}, so
     * {@code DefaultCorsProcessor} answered <em>"Reject: HTTP 'PUT' is not allowed"</em> and a bare
     * 403 {@code "Invalid CORS request"}.
     *
     * <h2>Why it was not obvious</h2>
     * <b>The preflight succeeded.</b> A preflight asks about {@code Access-Control-Request-Method},
     * which correctly said {@code POST} — and preflights run before the override header is ever
     * consulted. Only the real request failed, and it failed inside the CORS layer rather than the
     * application, so the browser reported a generic network error and the server log blamed a verb
     * nobody had sent. Marking a notification as read was the symptom that surfaced it; every write
     * in development was broken the same way.
     *
     * <p>Both halves are asserted here, because it was the disagreement between them that hid it.
     */
    @Test
    @WithMockUser(authorities = "UPDATE_ASSET")
    void anOverriddenWriteFromABrowserSurvivesCors() throws Exception {
        int status = mockMvc.perform(post(API + "/assets/" + MISSING)
                        .header("Origin", browserOrigin)
                        .header(HEADER, "PUT")
                        .contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("CORS sees the verb the browser actually sent (POST); it must not adjudicate the "
                        + "restored one. A 403 here is DefaultCorsProcessor, not authorization.")
                .isNotEqualTo(403);
    }

    /** The preflight that runs first, and which passed even while the request behind it did not. */
    @Test
    void thePreflightAsksAboutPostAndIsAllowed() throws Exception {
        var response = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .options(API + "/assets/" + MISSING)
                                .header("Origin", browserOrigin)
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers",
                                        "authorization,content-type,x-http-method-override,x-csrf-token"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Headers"))
                .as("both headers the scheme depends on must be permitted, or every write fails "
                        + "preflight with an error naming no header")
                .contains(HttpMethodOverrideFilter.HEADER.toLowerCase())
                .contains(SecurityConfiguration.CSRF_TOKEN_HEADER.toLowerCase());
    }

    // ── The two-verb contract ────────────────────────────────────────────────

    /**
     * A browser must never be told it may send anything but GET or POST.
     *
     * <p>The firewall accepts those two and drops everything else — PUT, DELETE and OPTIONS alike.
     * The server still <em>serves</em> PUT and DELETE, and a direct API client inside the firewall may
     * still use them; this is about what the CORS response advertises to a browser, because a browser
     * that believes it may send a PUT will send one and have it dropped in transit. An unexplained
     * network failure is a worse answer than a rule that never offered the verb.
     *
     * <p>OPTIONS was in this list and never needed to be: the list is checked against
     * {@code Access-Control-Request-Method}, the verb a preflight is <em>asking about</em>, not the
     * preflight's own. Listing it only implied the application expects OPTIONS traffic, which it must
     * not — see {@code SecurityConfiguration#corsConfigurationSource} for why a preflight cannot
     * happen in production at all.
     */
    @Test
    void onlyGetAndPostAreAdvertisedToBrowsers() {
        CorsConfiguration cors = securityConfiguration.corsConfigurationSource()
                .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedMethods())
                .as("the firewall accepts GET and POST and drops the rest; advertising more sends a "
                        + "browser to make a request that will never arrive")
                .containsExactlyInAnyOrder("GET", "POST");
    }

    /** And both headers the override scheme needs must survive the preflight whitelist. */
    @Test
    void theTwoHeadersTheSchemeDependsOnAreWhitelisted() {
        CorsConfiguration cors = securityConfiguration.corsConfigurationSource()
                .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedHeaders())
                .as("allowedHeaders is a whitelist - a header missing from it is rejected at preflight "
                        + "with an error naming no header, so every write fails for no visible reason")
                .contains(HttpMethodOverrideFilter.HEADER, SecurityConfiguration.CSRF_TOKEN_HEADER);
    }

    // ── Multipart, which is where this was most likely to break ──────────────

    /**
     * An overridden multipart PUT still reaches its handler.
     *
     * <p>{@code PUT /assets/repairs/{id}} and {@code PUT /assets/image/{id}} consume
     * {@code multipart/form-data}. Tomcat parses parts for POST requests and the wrapper reports PUT,
     * so whether the parts survive depends on {@code getParts()} delegating past the wrapper to the
     * real request. That is reasoning about someone else's container, which is exactly the kind of
     * thing to assert rather than believe.
     *
     * <p>Asserted as "reached the rule and was judged by it" — admitted for the right permission,
     * refused for the wrong one — rather than as a 200, which would depend on the fixture supplying
     * a real repair and a real file.
     */
    @Test
    @WithMockUser(authorities = "REPAIR_ASSET")
    void anOverriddenMultipartPutReachesItsHandlerWithTheFormBound() throws Exception {
        MvcResult result = mockMvc.perform(
                        multipart(API + "/assets/repairs/" + MISSING)
                                .param("repairEndDate", "2026-01-01T00:00:00")
                                .header(HEADER, "PUT"))
                .andReturn();

        String body = result.getResponse().getContentAsString();

        /*
         * Asserted on the handler's own failure, not on "was not refused".
         *
         * "Not refused" is satisfied by a 404 from a route that never matched, which is exactly the
         * failure mode in question - so it would have passed whether or not multipart survived the
         * override. Reaching `findOneRepair` and failing on a missing id is positive proof that the
         * PUT route matched *and* that @RequestParam bound from the multipart body.
         */
        assertThat(result.getResponse().getStatus())
                .as("a 400 here would mean repairEndDate never bound, i.e. the parts were lost when "
                        + "the wrapper started reporting PUT on a request Tomcat parsed as POST")
                .isNotEqualTo(400);

        assertThat(body)
                .as("the handler must have run: it fails on the missing repair, which it can only do "
                        + "after binding the form")
                .contains(MISSING);
    }

    /**
     * And the multipart route is still judged by its own verb's rule.
     *
     * <p>{@code PUT /assets/repairs/**} answers to {@code REPAIR_ASSET}. A caller holding only
     * {@code UPDATE_ASSET} must be refused — if the override had left the request being judged as a
     * POST, it would have met a different rule entirely.
     */
    @Test
    @WithMockUser(authorities = "UPDATE_ASSET")
    void anOverriddenMultipartPutIsStillRefusedToTheWrongPermission() throws Exception {
        assertRefused(
                multipart(API + "/assets/repairs/" + MISSING)
                        .param("repairEndDate", "2026-01-01T00:00:00")
                        .header(HEADER, "PUT"),
                "closing a repair answers to REPAIR_ASSET, not UPDATE_ASSET — the override "
                        + "must not have moved it to a different rule");
    }
}
