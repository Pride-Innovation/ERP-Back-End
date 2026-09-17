package pride.bank.erp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import pride.bank.erp.advice.AccessDenied;
import pride.bank.erp.user.UserRepository;

import java.lang.reflect.Method;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * "I do not know who you are" and "the answer is no" are different answers.
 *
 * <h2>The regression this exists to stop coming back</h2>
 * When refusals moved off 403 — the firewall blocks that status — {@code InsufficientAuthentication}
 * moved with {@code AccessDenied}, on the reasoning that both were "authorization". They are not, and
 * conflating them broke session expiry outright:
 *
 * <ul>
 *   <li>An expired token makes {@code JwtService.extractUserEmail} return null, so
 *       {@code JwtAuthenticationFilter} sets no authentication and the request reaches
 *       {@code AuthorizationFilter} as <b>anonymous</b>.</li>
 *   <li>{@code ExceptionTranslationFilter} then calls the entry point, which raises
 *       {@code InsufficientAuthenticationException("Full authentication is required…")}.</li>
 *   <li>Mapped to 422, that never matched the front end's {@code status === 401} refresh branch. No
 *       refresh was attempted, nobody was signed out, and the error surfaced in the browser as an
 *       uncaught {@code AxiosError} on top of whatever page was open.</li>
 * </ul>
 *
 * <p>Measured before the fix: no header, a garbage token and a correctly-signed expired token
 * <b>all returned 422</b>.
 *
 * <h2>The rule</h2>
 * <b>401</b> means re-authenticate — refresh, or sign in again. <b>{@link AccessDenied#STATUS}</b>
 * means the caller is known and the answer is no, where refreshing changes nothing and signing them
 * out would be wrong. Only 403 is unavailable; 401 was never in question.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionExpiryTest {

    private static final String PROTECTED = "/api/v1/audit/trails";
    private static final String REFRESH = "/api/v1/auth/refresh-token";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository users;

    /** A correctly signed token whose expiry is already in the past. */
    private String expiredToken() throws Exception {
        Method build = JwtService.class.getDeclaredMethod(
                "buildToken", java.util.Map.class, UserDetails.class, long.class);
        build.setAccessible(true);
        return (String) build.invoke(jwtService, new HashMap<String, Object>(), users.findAll().get(0), -60_000L);
    }

    private MvcResult call(String path, String bearer) throws Exception {
        var request = path.equals(REFRESH) ? post(path) : get(path);
        if (bearer != null) request = request.header("Authorization", "Bearer " + bearer);
        return mockMvc.perform(request).andReturn();
    }

    // ── Not authenticated ────────────────────────────────────────────────────

    @Test
    void anExpiredTokenIsRejectedAsUnauthenticated() throws Exception {
        MvcResult result = call(PROTECTED, expiredToken());

        assertThat(result.getResponse().getStatus())
                .as("the front end refreshes on 401 and on nothing else; any other status leaves an "
                        + "expired session alive on screen until something throws")
                .isEqualTo(401);
    }

    @Test
    void noTokenAtAllIsRejectedAsUnauthenticated() throws Exception {
        assertThat(call(PROTECTED, null).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aMalformedTokenIsRejectedAsUnauthenticated() throws Exception {
        assertThat(call(PROTECTED, "not.a.jwt").getResponse().getStatus()).isEqualTo(401);
    }

    /** Named, so the browser can tell an ended session from a server that is simply unhappy. */
    @Test
    void theRefusalNamesItselfAsAnEndedSession() throws Exception {
        assertThat(call(PROTECTED, expiredToken()).getResponse().getContentAsString())
                .contains("SESSION_EXPIRED");
    }

    // ── Authenticated, and refused ───────────────────────────────────────────

    /**
     * The other half of the distinction, and the reason this is not simply "everything is 401".
     *
     * <p>A caller the application knows, asking for something they may not have, must <b>not</b> get
     * 401 — the front end would refresh a perfectly good token and then sign them out for opening
     * another branch's record.
     */
    @Test
    @WithMockUser(authorities = "CREATE_ASSET")
    void beingKnownAndRefusedIsNotAnExpiredSession() throws Exception {
        int status = mockMvc.perform(get("/api/v1/assets/999999999"))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("READ_ASSET is missing, so this is a refusal rather than an unknown caller")
                .isEqualTo(AccessDenied.STATUS.value())
                .isNotEqualTo(401);
    }

    // ── The refresh endpoint ─────────────────────────────────────────────────

    /**
     * Every failure path used to answer <b>200 with an empty body</b>.
     *
     * <p>No header returned early; an expired or malformed token left the email null; an invalid one
     * failed the check — and all three fell out of the method having written nothing. The browser
     * read {@code data.accessToken} off an empty body, got undefined, and stored the string
     * "undefined" as a credential before concluding the refresh had failed.
     *
     * <p>A response that says "fine" while handing back nothing is the worst shape a failure can
     * take, because every caller has to know that success is not success.
     */
    @Test
    void refreshingWithoutATokenIsRefusedRatherThanAnsweredEmpty() throws Exception {
        MvcResult result = call(REFRESH, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString())
                .as("and it says what to do about it")
                .contains("refresh token is required");
    }

    @Test
    void refreshingWithAnExpiredTokenIsRefused() throws Exception {
        MvcResult result = call(REFRESH, expiredToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).contains("session has ended");
    }

    @Test
    void refreshingWithAMalformedTokenIsRefused() throws Exception {
        assertThat(call(REFRESH, "not.a.jwt").getResponse().getStatus()).isEqualTo(401);
    }
}
