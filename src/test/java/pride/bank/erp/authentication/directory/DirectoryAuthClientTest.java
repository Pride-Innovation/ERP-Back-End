/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.authentication.directory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The LDAP Authentication API client against a mock server - never the real one, because five
 * failures in fifteen minutes throttle a real employee's username across every bank system.
 */
class DirectoryAuthClientTest {

    private static final String URL = "https://ldap-auth.test/api/auth/authenticate";

    private DirectoryAuthSettings settings;
    private MockRestServiceServer server;
    private DirectoryAuthClient client;

    @BeforeEach
    void setUp() {
        settings = settings("https://ldap-auth.test/", "erp-dev", "secret-key");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DirectoryAuthClient(settings, new ObjectMapper(), builder.build());
    }

    static DirectoryAuthSettings settings(String baseUrl, String clientId, String apiKey) {
        DirectoryAuthSettings s = new DirectoryAuthSettings();
        ReflectionTestUtils.setField(s, "mode", DirectoryAuthSettings.Mode.LDAP);
        ReflectionTestUtils.setField(s, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(s, "clientId", clientId);
        ReflectionTestUtils.setField(s, "apiKey", apiKey);
        ReflectionTestUtils.setField(s, "connectTimeoutMs", 5000L);
        ReflectionTestUtils.setField(s, "readTimeoutMs", 10000L);
        ReflectionTestUtils.setField(s, "retryDelayMs", 0L);
        return s;
    }

    private static String answer(boolean authenticated, String code, String username) {
        String user = username == null ? "" : ", \"user\": {\"username\": \"" + username
                + "\", \"displayName\": \"Jane Doe\", \"email\": \"" + username + "@pridebank.co.ug\", \"groups\": [\"Finance\"]}";
        return "{\"authenticated\": " + authenticated + ", \"code\": \"" + code + "\", \"message\": \"m\"" + user + "}";
    }

    @Test
    void sendsBothApplicationCredentialsAndThePasswordExactlyAsTyped() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Client-Id", "erp-dev"))
                .andExpect(header("X-Api-Key", "secret-key"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                // Leading and trailing spaces belong to the password: the guide says do not trim.
                .andExpect(content().json("{\"username\": \"jdoe\", \"password\": \"  P@ss word \"}", true))
                .andRespond(withSuccess(answer(true, "AUTHENTICATED", "jdoe"), MediaType.APPLICATION_JSON));

        DirectoryResult result = client.authenticate("jdoe", "  P@ss word ");

        assertThat(result.authenticated()).isTrue();
        assertThat(result.username()).isEqualTo("jdoe");
        assertThat(result.email()).isEqualTo("jdoe@pridebank.co.ug");
        server.verify();
    }

    @Test
    void aRefusedPasswordIsNeverRetried() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body(answer(false, "INVALID_CREDENTIALS", null)));

        DirectoryResult result = client.authenticate("jdoe", "wrong");

        assertThat(result.authenticated()).isFalse();
        assertThat(result.code()).isEqualTo(DirectoryResult.INVALID_CREDENTIALS);
        server.verify();
    }

    @Test
    void aThrottledUsernameIsNeverRetried() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body(answer(false, "USER_THROTTLED", null)));

        assertThat(client.authenticate("jdoe", "x").code()).isEqualTo(DirectoryResult.USER_THROTTLED);
        server.verify();
    }

    @Test
    void anUnavailableServiceIsRetriedOnce() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                        .body(answer(false, "SERVICE_UNAVAILABLE", null)));
        server.expect(requestTo(URL))
                .andRespond(withSuccess(answer(true, "AUTHENTICATED", "jdoe"), MediaType.APPLICATION_JSON));

        assertThat(client.authenticate("jdoe", "x").authenticated()).isTrue();
        server.verify();
    }

    @Test
    void rateLimitingIsRetriedOnceAndThenGivenUpOn() {
        server.expect(ExpectedCount.twice(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body(answer(false, "RATE_LIMITED", null)));

        DirectoryResult result = client.authenticate("jdoe", "x");

        assertThat(result.authenticated()).isFalse();
        assertThat(result.code()).isEqualTo(DirectoryResult.RATE_LIMITED);
        server.verify();
    }

    /** The API may have checked the password before the answer was lost; a retry would count it twice. */
    @Test
    void aTimeoutIsNotRetriedAndFailsClosed() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

        DirectoryResult result = client.authenticate("jdoe", "x");

        assertThat(result.authenticated()).isFalse();
        assertThat(result.code()).isEqualTo(DirectoryResult.UNAVAILABLE);
        server.verify();
    }

    @Test
    void authenticatedWithoutAUserIsNotASignIn() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"authenticated\": true, \"code\": \"AUTHENTICATED\"}", MediaType.APPLICATION_JSON));

        DirectoryResult result = client.authenticate("jdoe", "x");

        assertThat(result.authenticated()).isFalse();
        assertThat(result.code()).isEqualTo(DirectoryResult.UNEXPECTED);
    }

    @Test
    void anUnreadableAnswerFailsClosed() {
        server.expect(requestTo(URL)).andRespond(withSuccess("<html>blocked</html>", MediaType.TEXT_HTML));

        DirectoryResult result = client.authenticate("jdoe", "x");

        assertThat(result.authenticated()).isFalse();
        assertThat(result.code()).isEqualTo(DirectoryResult.UNEXPECTED);
    }

    @Test
    void theApplicationsOwnBadCredentialsAreReported() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body(answer(false, "INVALID_CLIENT", null)));

        assertThat(client.authenticate("jdoe", "x").code()).isEqualTo(DirectoryResult.INVALID_CLIENT);
        server.verify();
    }

    @Test
    void anUnconfiguredClientCallsNothingAndRefuses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer silent = MockRestServiceServer.bindTo(builder).build();
        DirectoryAuthClient unconfigured =
                new DirectoryAuthClient(settings("https://ldap-auth.test", "erp-dev", ""), new ObjectMapper(), builder.build());

        DirectoryResult result = unconfigured.authenticate("jdoe", "x");

        assertThat(result.authenticated()).isFalse();
        assertThat(result.code()).isEqualTo(DirectoryResult.UNAVAILABLE);
        silent.verify();
    }

    /** Spring's DEBUG logging prints request bodies through toString. */
    @Test
    void theRequestBodyNeverPrintsThePassword() {
        assertThat(new DirectoryAuthClient.Credentials("jdoe", "Secret#123").toString())
                .contains("jdoe")
                .doesNotContain("Secret#123");
    }

    @Test
    void plainHttpIsRefusedAtStartup() {
        DirectoryAuthSettings insecure = settings("http://ldap-auth.bank", "erp-dev", "k");
        org.assertj.core.api.Assertions.assertThatThrownBy(insecure::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https");
    }
}
