/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.authentication.directory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.List;

/**
 * Calls the bank's LDAP Authentication API ({@code POST /api/auth/authenticate}). Server-side only:
 * the browser never sees the API, its key, or its answer.
 *
 * <p>Rules from the integration guide, each of which is easy to break quietly:
 * <ul>
 *   <li>The password is sent exactly as typed - not trimmed, hashed or encoded - and is never logged.
 *       {@link Credentials#toString()} redacts it, because Spring's DEBUG logging prints request
 *       bodies through {@code toString}.</li>
 *   <li>Retry once, and only for {@code RATE_LIMITED} and {@code SERVICE_UNAVAILABLE}. Never for a
 *       refused credential: each repeat counts toward the shared five-failures throttle.</li>
 *   <li>Fail closed: anything that is not an explicit {@code authenticated: true} is a refusal.</li>
 *   <li>5 s to connect, 10 s to answer.</li>
 * </ul>
 */
@Component
@Slf4j
public class DirectoryAuthClient {

    static final String AUTHENTICATE_PATH = "/api/auth/authenticate";

    private final DirectoryAuthSettings settings;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public DirectoryAuthClient(DirectoryAuthSettings settings, ObjectMapper objectMapper) {
        this(settings, objectMapper, RestClient.builder().requestFactory(requestFactory(settings)).build());
    }

    /** For tests, which bind a mock server to the client. */
    DirectoryAuthClient(DirectoryAuthSettings settings, ObjectMapper objectMapper, RestClient restClient) {
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    private static JdkClientHttpRequestFactory requestFactory(DirectoryAuthSettings settings) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(settings.readTimeout());
        return factory;
    }

    public DirectoryResult authenticate(String username, String password) {
        if (!settings.isConfigured()) {
            log.error("Sign-in refused: the LDAP Authentication API is not configured");
            return DirectoryResult.failure(DirectoryResult.UNAVAILABLE);
        }

        DirectoryResult result = callOnce(username, password);
        if (result.retryable()) {
            log.warn("LDAP Authentication API answered {}; retrying once", result.code());
            pause();
            result = callOnce(username, password);
        }

        switch (result.code()) {
            case DirectoryResult.INVALID_CLIENT, DirectoryResult.CLIENT_INACTIVE ->
                    // The application's own registration is broken: the guide asks for an alert.
                    log.error("LDAP Authentication API rejected this application's credentials ({}). "
                            + "Check LDAP_AUTH_CLIENT_ID / LDAP_AUTH_API_KEY for this environment.", result.code());
            case DirectoryResult.SERVICE_UNAVAILABLE, DirectoryResult.UNAVAILABLE, DirectoryResult.UNEXPECTED,
                 DirectoryResult.RATE_LIMITED ->
                    log.error("LDAP Authentication API could not sign anyone in ({})", result.code());
            default -> { }
        }
        return result;
    }

    private DirectoryResult callOnce(String username, String password) {
        try {
            return restClient.post()
                    .uri(settings.getBaseUrl().trim().replaceAll("/+$", "") + AUTHENTICATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("X-Client-Id", settings.getClientId())
                    .header("X-Api-Key", settings.getApiKey())
                    .body(new Credentials(username, password))
                    .exchange((request, response) -> read(response.getStatusCode().value(), response.getBody().readAllBytes()));
        } catch (RestClientException e) {
            // Timeouts, refused connections, TLS failures. The message names the host, never the body.
            log.error("LDAP Authentication API unreachable: {}", e.getClass().getSimpleName());
            return DirectoryResult.failure(DirectoryResult.UNAVAILABLE);
        }
    }

    private DirectoryResult read(int status, byte[] body) {
        Answer answer;
        try {
            answer = body.length == 0 ? null : objectMapper.readValue(body, Answer.class);
        } catch (Exception e) {
            log.error("LDAP Authentication API returned an unreadable body (HTTP {})", status);
            return DirectoryResult.failure(DirectoryResult.UNEXPECTED);
        }
        if (answer == null) {
            return DirectoryResult.failure(status == 503 ? DirectoryResult.SERVICE_UNAVAILABLE : DirectoryResult.UNEXPECTED);
        }
        // Authenticated only when the flag says so, the code agrees, and a user came back with it.
        if (Boolean.TRUE.equals(answer.authenticated()) && status == 200
                && DirectoryResult.AUTHENTICATED.equals(answer.code())
                && answer.user() != null && answer.user().username() != null) {
            return DirectoryResult.success(answer.user().username(), answer.user().email());
        }
        if (Boolean.TRUE.equals(answer.authenticated())) {
            log.error("LDAP Authentication API said authenticated without a usable answer (HTTP {}, code {})", status, answer.code());
            return DirectoryResult.failure(DirectoryResult.UNEXPECTED);
        }
        return DirectoryResult.failure(answer.code());
    }

    private void pause() {
        try {
            Thread.sleep(Math.max(0, settings.getRetryDelayMs()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The request body. */
    record Credentials(String username, String password) {
        @Override
        public String toString() {
            return "Credentials[username=" + username + ", password=<redacted>]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Answer(Boolean authenticated, String code, String message, AnswerUser user) {
    }

    /** {@code groups} is read and ignored: authorization stays in this application. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnswerUser(String username, String displayName, String email, List<String> groups) {
    }
}
