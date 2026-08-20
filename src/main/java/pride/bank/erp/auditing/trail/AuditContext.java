/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.trail;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.user.User;

/**
 * Works out who is acting and from where, so call sites don't have to.
 *
 * <p>Every {@code record(...)} call would otherwise need an {@code HttpServletRequest} threaded down
 * to it, which would mean changing the signature of most service methods in the application to carry
 * a parameter only the audit trail wants. Instead this reads the ambient request and security
 * context, both of which Spring already binds to the thread.
 *
 * <p>Everything here degrades to null rather than throwing. Seeders, scheduled jobs and tests run
 * with no bound request and often no authentication; an audit helper that blew up in those
 * conditions would take the operation down with it.
 */
@Component
public class AuditContext {

    /** Longest IPv6 literal with an embedded IPv4 tail; matches {@code audit_event.ip_address}. */
    private static final int IP_MAX = 45;
    private static final int USER_AGENT_MAX = 255;

    /**
     * The signed-in user, or null.
     *
     * <p>Read from the security context rather than {@code CurrentUserService}, which re-parses the
     * JWT and hits the database on every call — too expensive for something invoked on nearly every
     * write. {@code JwtAuthenticationFilter} has already put the {@link User} there.
     */
    public User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return (principal instanceof User user) ? user : null;
    }

    /**
     * The caller's IP address.
     *
     * <p>{@code getRemoteAddr()} alone is wrong once this sits behind a load balancer or reverse
     * proxy: every row would record the proxy's address and the trail would be useless for telling
     * one user's session from another's. {@code X-Forwarded-For} accumulates hops left-to-right, so
     * the first entry is the original client.
     *
     * <p>That header is client-supplied and therefore spoofable. It is recorded as evidence, not
     * relied on for any decision — nothing in this application authorises anything by IP.
     */
    public String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return truncate(first, IP_MAX);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp.trim(), IP_MAX);
        }
        return truncate(request.getRemoteAddr(), IP_MAX);
    }

    /** The browser or client that made the call, for spotting a session that doesn't look right. */
    public String userAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        return truncate(request.getHeader("User-Agent"), USER_AGENT_MAX);
    }

    /** Null outside a web request — a seeder, a scheduled job, a test. */
    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return (attributes instanceof ServletRequestAttributes servlet) ? servlet.getRequest() : null;
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
