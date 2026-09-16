package pride.bank.erp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pride.bank.erp.advice.AccessDenied;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Every method-specific rule, checked against the verb arriving in a header instead of on the wire.
 *
 * <h2>Why this exists beside {@link MethodOverrideSecurityTest}</h2>
 * That one proves the <em>mechanism</em>: the filter runs before Spring Security, so an overridden
 * verb is judged by its own rule. It samples four routes. This one is the <b>audit</b> — it walks
 * every rule in {@link SecurityConfiguration} that names {@code HttpMethod.PUT} or
 * {@code HttpMethod.DELETE} and checks both directions on each:
 *
 * <ul>
 *   <li><b>Refused</b> for an authority that must not open it — almost always the authority the
 *       <em>POST</em> rule on the same path grants, because that is the exact escalation the override
 *       makes possible: reach the PUT or DELETE handler while being judged as a POST.</li>
 *   <li><b>Admitted</b> for the authority that owns the verb. This is the half that proves the
 *       override is doing anything at all: if the verb were <em>not</em> restored, the request would
 *       be judged by the POST rule, which this caller does not satisfy, and would be refused.</li>
 * </ul>
 *
 * <p>Both directions matter and neither is sufficient alone. Refusal alone would pass if the override
 * silently did nothing; admission alone would pass if authorization had been removed entirely.
 *
 * <h2>The sixty-odd endpoints this does not need to cover</h2>
 * The other PUT and DELETE endpoints are guarded by {@code @PreAuthorize} on the handler method
 * rather than by a path matcher, and those are <b>safe by construction</b>: the annotation is
 * evaluated after routing, on the method that was actually selected, so an overridden verb cannot
 * land on one handler while being judged by another's rule. The escalation is only possible where
 * the rule is chosen by {@code (method, path)} before routing — which is exactly this list.
 *
 * <p>Two of them are spot-checked at the end anyway, because "safe by construction" is a claim about
 * Spring's ordering rather than about this application, and it is cheap to hold it to account.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("overridden verbs are judged by their own rule, on every method-specific route")
class OverriddenVerbAuthorizationMatrixTest {

    private static final String API = "/api/v1";
    private static final String HEADER = HttpMethodOverrideFilter.HEADER;

    /** Nothing owns this, so a request that gets past the rule fails in the handler instead. */
    private static final String MISSING = "999999999";

    @Autowired
    private MockMvc mockMvc;

    /**
     * One rule from {@link SecurityConfiguration}.
     *
     * @param path      a concrete path the route pattern matches
     * @param verb      the verb that travels in the header
     * @param owner     the authority that rule grants the route to
     * @param decoy     an authority that must <em>not</em> open it — the POST rule's authority on the
     *                  same path wherever one exists, since that is the reachable escalation
     */
    private record Rule(String path, String verb, String owner, String decoy) {
        @Override
        public String toString() {
            return verb + " " + path + "  owner=" + owner + "  decoy=" + decoy;
        }
    }

    /**
     * Every {@code HttpMethod.PUT} / {@code HttpMethod.DELETE} matcher in {@code SecurityConfiguration}.
     *
     * <p>Written out rather than reflected off the configuration on purpose. A test that derives its
     * expectations from the thing it is testing agrees with it by construction — including when the
     * configuration is wrong. This list was read off the file by hand, and if a rule is added without
     * a row here, {@code theListCoversEveryMethodSpecificRule} fails.
     */
    static Stream<Rule> rules() {
        return Stream.of(
                // Users — POST on this path is CREATE_USER, so that is the decoy for both.
                new Rule("/users/" + MISSING, "PUT", "UPDATE_USER", "CREATE_USER"),
                new Rule("/users/" + MISSING, "DELETE", "DELETE_USER", "CREATE_USER"),

                // Roles
                new Rule("/roles/" + MISSING, "PUT", "UPDATE_ROLE", "CREATE_ROLE"),
                new Rule("/roles/" + MISSING, "DELETE", "DELETE_ROLE", "CREATE_ROLE"),

                // Permissions
                new Rule("/permissions/" + MISSING, "PUT", "UPDATE_PERMISSION", "CREATE_PERMISSION"),
                new Rule("/permissions/" + MISSING, "DELETE", "DELETE_PERMISSION", "CREATE_PERMISSION"),

                // Requests
                new Rule("/requests/" + MISSING, "PUT", "UPDATE_REQUEST", "CREATE_REQUEST"),
                new Rule("/requests/" + MISSING, "DELETE", "DELETE_REQUEST", "CREATE_REQUEST"),

                // Statuses
                new Rule("/statuses/" + MISSING, "PUT", "UPDATE_STATUS", "CREATE_STATUS"),
                new Rule("/statuses/" + MISSING, "DELETE", "DELETE_STATUS", "CREATE_STATUS"),

                // Stock orders — INVENTORY_ROUTE is /stocks/**, not /inventory/**.
                new Rule("/stocks/" + MISSING, "PUT", "UPDATE_INVENTORY", "CREATE_INVENTORY"),
                new Rule("/stocks/" + MISSING, "DELETE", "DELETE_INVENTORY", "CREATE_INVENTORY"),

                // Assets
                new Rule("/assets/" + MISSING, "PUT", "UPDATE_ASSET", "CREATE_ASSET"),
                new Rule("/assets/" + MISSING, "DELETE", "DELETE_ASSET", "CREATE_ASSET"),

                /*
                 * Two narrower asset routes, matched *before* /assets/** and therefore answering to
                 * their own permissions. The decoy here is UPDATE_ASSET rather than a POST authority:
                 * POST on the repairs path is also REPAIR_ASSET, so there is no verb escalation to
                 * test - what must hold is that the override does not let a holder of the broad
                 * asset-edit permission through a narrower rule.
                 */
                new Rule("/assets/repairs/" + MISSING, "PUT", "REPAIR_ASSET", "UPDATE_ASSET"),
                new Rule("/assets/store/" + MISSING, "PUT", "RECEIVE_ASSET_IN_STORE", "UPDATE_ASSET")
        );
    }

    // ── The audit ────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    void theWrongAuthorityIsStillRefusedWhenTheVerbArrivesInAHeader(Rule rule) throws Exception {
        int status = statusOf(rule, rule.decoy());

        assertThat(status)
                .as("%s must not open %s %s. If this passes as a POST, the override has become a way "
                        + "of choosing which rule to be judged by.", rule.decoy(), rule.verb(), rule.path())
                .isEqualTo(AccessDenied.STATUS.value());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    void theOwningAuthorityIsAdmittedWhenTheVerbArrivesInAHeader(Rule rule) throws Exception {
        int status = statusOf(rule, rule.owner());

        assertThat(status)
                .as("%s owns %s %s and must reach it. A refusal here means the verb was never restored "
                        + "- the request was judged by the POST rule, which this caller does not hold.",
                        rule.owner(), rule.verb(), rule.path())
                .isNotEqualTo(AccessDenied.STATUS.value());
    }

    // ── The list must not fall behind the configuration ──────────────────────

    /**
     * A rule added to {@code SecurityConfiguration} without a row above is a rule nobody checked.
     *
     * <p>Counts rather than parses: the point is to force whoever adds the eighteenth matcher to come
     * here and think about its decoy, not to re-derive the table automatically — which would make the
     * expectations agree with the configuration by construction, wrong included.
     */
    @org.junit.jupiter.api.Test
    void theListCoversEveryMethodSpecificRule() throws Exception {
        String config = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/pride/bank/erp/config/SecurityConfiguration.java"));

        /*
         * Comments stripped, and matcher calls only.
         *
         * Counting every line that mentions HttpMethod.PUT counts the prose explaining why the rules
         * are method-specific, and this test failed on a comment the moment one was added. A source
         * assertion about code must read code: strip the comments, then count the lines that are
         * actually registering a rule.
         */
        String code = config
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");

        long declared = code.lines()
                .filter(line -> line.contains("requestMatchers("))
                .filter(line -> line.contains("HttpMethod.PUT") || line.contains("HttpMethod.DELETE"))
                .count();

        /*
         * Seventeen matcher lines, sixteen rows.
         *
         * The odd one out is the PUT on USER_IMAGE_ROUTE / USER_IMAGE_REMOVE_ROUTE, which resolves to
         * `.authenticated()` rather than an authority - there is no wrong authority to hand it, so it
         * has no decoy and no row. It is covered instead by `aPutGuardedOnlyByAuthenticationStillNeedsALogin`.
         */
        assertThat(declared)
                .as("a PUT or DELETE matcher was added to SecurityConfiguration without a row in "
                        + "rules(). Add one - including the authority that must NOT open it.")
                .isEqualTo(17);

        assertThat(rules().count()).isEqualTo(16);
    }

    /** The one method-specific rule that grants on authentication rather than an authority. */
    @org.junit.jupiter.api.Test
    void aPutGuardedOnlyByAuthenticationStillNeedsALogin() throws Exception {
        int anonymous = mockMvc.perform(post(API + "/users/image/" + MISSING).header(HEADER, "PUT"))
                .andReturn().getResponse().getStatus();

        assertThat(anonymous)
                .as("updating a profile picture is authenticated-only, but authenticated-only is not "
                        + "open - an overridden verb must not slip past the login requirement")
                .isIn(401, AccessDenied.STATUS.value());
    }

    // ── @PreAuthorize routes: safe by construction, held to it anyway ─────────

    /**
     * A handler-level guard cannot be escaped by the override, because it is attached to the method
     * the router already picked. Spot-checked on two of the thirteen.
     */
    @ParameterizedTest(name = "{0} {1} needs {2}")
    @MethodSource("preAuthorizeSpotChecks")
    void handlerLevelGuardsStillApplyToAnOverriddenVerb(String path, String verb,
                                                        String owner, String decoy) throws Exception {
        assertThat(statusOf(new Rule(path, verb, owner, decoy), decoy))
                .as("%s must not reach %s %s", decoy, verb, path)
                .isEqualTo(AccessDenied.STATUS.value());
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> preAuthorizeSpotChecks() {
        return Stream.of(
                // The settings routes the approval-workflows page calls, which is where this question
                // was asked from: toggle-active is a PUT and must still demand UPDATE_SETTING.
                org.junit.jupiter.params.provider.Arguments.of(
                        "/approval-workflows/" + MISSING + "/toggle-active", "PUT",
                        "UPDATE_SETTING", "READ_SETTING"),
                org.junit.jupiter.params.provider.Arguments.of(
                        "/stores/" + MISSING + "/name", "PUT",
                        "UPDATE_STORE", "READ_STORE")
        );
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private int statusOf(Rule rule, String authority) throws Exception {
        MockHttpServletRequestBuilder request = post(API + rule.path())
                .header(HEADER, rule.verb())
                .with(SecurityMockMvcRequestPostProcessors.user("probe")
                        .authorities(List.of(new org.springframework.security.core.authority
                                .SimpleGrantedAuthority(authority))))
                .contentType("application/json")
                .content("{}");
        try {
            return mockMvc.perform(request).andReturn().getResponse().getStatus();
        } catch (Exception handlerThrew) {
            // Reaching a handler that then fails is proof the rule admitted the request. Several read
            // the caller off the principal and cast it to the application's own User, which a mock
            // principal does not supply — a fact about the fixture, not about the permission.
            return 200;
        }
    }
}
