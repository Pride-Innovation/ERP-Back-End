/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.authentication.directory;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pride.bank.erp.exception.SignInException;

import java.net.URI;
import java.time.Duration;

/**
 * How sign-in checks a password: against the bank's LDAP Authentication API, or against the hash
 * stored in this application's database.
 *
 * <p>{@code LDAP} is the default, so an environment that sets nothing fails closed - nobody signs in
 * until the API is configured - rather than quietly falling back to local passwords. {@code LOCAL}
 * exists for development machines that cannot reach the API.
 *
 * <p>Instance fields on purpose: {@code @Value} on a static field injects nothing (see
 * {@code StaticValueInjectionTest}).
 */
@Component
@Getter
@Slf4j
public class DirectoryAuthSettings {

    public enum Mode { LDAP, LOCAL }

    @Value("${application.directory-auth.mode:LDAP}")
    private Mode mode;

    @Value("${application.directory-auth.base-url:}")
    private String baseUrl;

    @Value("${application.directory-auth.client-id:}")
    private String clientId;

    /** The API key. Never logged, never returned, never put in an exception message. */
    @Value("${application.directory-auth.api-key:}")
    private String apiKey;

    @Value("${application.directory-auth.connect-timeout-ms:5000}")
    private long connectTimeoutMs;

    @Value("${application.directory-auth.read-timeout-ms:10000}")
    private long readTimeoutMs;

    /** The pause before the single retry of a rate-limited or unavailable call. */
    @Value("${application.directory-auth.retry-delay-ms:1000}")
    private long retryDelayMs;

    public boolean isLdap() {
        return mode == Mode.LDAP;
    }

    public Duration connectTimeout() {
        return Duration.ofMillis(connectTimeoutMs);
    }

    public Duration readTimeout() {
        return Duration.ofMillis(readTimeoutMs);
    }

    public static final String PASSWORD_MANAGED_BY_AD = "PASSWORD_MANAGED_BY_AD";

    /**
     * Refuses a password action - change, reset request, reset - while passwords live in AD. Setting
     * the local hash would succeed, change nothing anyone signs in with, and tell the person it had
     * worked.
     */
    public void requireLocalPasswords() {
        if (isLdap()) {
            throw new SignInException(HttpStatus.BAD_REQUEST, PASSWORD_MANAGED_BY_AD,
                    "Passwords are managed in Active Directory. Please contact the IT Service Desk to change or reset yours.");
        }
    }

    /** Configured enough to make a call. When false, every sign-in fails closed. */
    public boolean isConfigured() {
        return !isBlank(baseUrl) && !isBlank(clientId) && !isBlank(apiKey);
    }

    /**
     * Refuses to start over plain HTTP, which would put every employee's AD password on the wire in
     * the clear. Missing credentials only log: the application still starts, and sign-in fails closed.
     */
    @PostConstruct
    void validate() {
        if (!isLdap()) {
            log.warn("Sign-in is in LOCAL mode: passwords are checked against this application's database, "
                    + "not the LDAP Authentication API. Use this on a development machine only.");
            return;
        }
        if (!isConfigured()) {
            log.error("Sign-in is in LDAP mode but LDAP_AUTH_BASE_URL, LDAP_AUTH_CLIENT_ID or LDAP_AUTH_API_KEY "
                    + "is not set. Every sign-in will be refused until they are.");
            return;
        }
        URI uri = URI.create(baseUrl.trim());
        boolean local = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !local) {
            throw new IllegalStateException("LDAP_AUTH_BASE_URL must use https: it carries employees' passwords");
        }
        log.info("Sign-in is in LDAP mode against {}", uri.getHost());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
