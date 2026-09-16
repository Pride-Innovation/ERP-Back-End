/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Lets a POST stand in for a PUT or a DELETE, because the firewall refuses those verbs.
 *
 * <h2>Why</h2>
 * The web application firewall in front of this service blocks PUT and DELETE outright, at every
 * path. The application has <b>40 {@code @PutMapping}s and 19 {@code @DeleteMapping}s</b>, so either
 * every one of them is rewritten as a POST — losing the distinction the routes and the security
 * rules are built on — or the verb travels in a header and is restored before anything looks at it.
 * This does the second, so no controller changes and no security rule changes.
 *
 * <h2>Where this sits, and why both neighbours matter</h2>
 * Registered inside the security chain by {@code SecurityConfiguration}, <b>after</b> Spring's
 * {@code CorsFilter} and <b>before</b> {@code AuthorizationFilter}. Not at the front of the servlet
 * chain, and not at the back — the window between those two is the only correct place, and each
 * boundary is there for its own reason.
 *
 * <p><b>Before authorization</b>, because authorization is method-specific and would otherwise judge
 * the request as the POST it arrived as. That is a privilege escalation, spelled out below.
 *
 * <p><b>After CORS</b>, because CORS is about what the browser <em>actually sent</em>. This filter
 * was first registered as a servlet filter at {@code HIGHEST_PRECEDENCE}, in front of everything —
 * so {@code DefaultCorsProcessor} read a method of {@code PUT} from a request the browser had sent as
 * {@code POST}, did not find it in {@code allowedMethods}, and answered
 * <em>"Reject: HTTP 'PUT' is not allowed"</em> with a 403. The <em>preflight</em> passed, because a
 * preflight correctly asks about the wire verb; only the real request failed, which made it look like
 * a server fault rather than a CORS rule. Every write in development was broken by it.
 *
 * <p>The lesson worth keeping: <b>this filter makes {@code getMethod()} lie, and everything
 * downstream believes it.</b> Before adding a check that reads the method, decide which verb that
 * check is actually about — the one on the wire, or the one intended.
 *
 * <p>Authorization in this application is <b>method-specific</b>: seventeen rules in
 * {@code SecurityConfiguration} name {@code HttpMethod.PUT} or {@code HttpMethod.DELETE}, and the
 * same path routinely carries a different permission per verb:
 *
 * <pre>
 *   .requestMatchers(HttpMethod.DELETE, ASSET_ROUTE).hasAuthority(DELETE_ASSET)
 *   .requestMatchers(HttpMethod.POST,   ASSET_ROUTE).hasAuthority(CREATE_ASSET)
 * </pre>
 *
 * If the override were applied <em>after</em> Spring Security — in an interceptor, or a filter
 * registered later — then a holder of {@code CREATE_ASSET} alone could send
 * {@code POST /assets/{id}} with {@code X-HTTP-Method-Override: DELETE}. Spring Security would
 * evaluate the POST rule and admit it; the dispatcher would then route it to the
 * {@code @DeleteMapping} and the asset would be gone. Every one of those seventeen rules inverts the
 * same way. {@code MethodOverrideSecurityTest} asserts the refusal rather than the filter order,
 * because the order is a means and the refusal is the fact.
 *
 * <h2>Three deliberate narrowings</h2>
 * <ol>
 *   <li><b>Only on POST.</b> An override on a GET would let a request past a rule written for reads
 *       and into a handler that writes.</li>
 *   <li><b>Only PUT and DELETE.</b> Those are the two verbs the firewall blocks. Accepting anything
 *       a caller names turns this into a general way of choosing which security rule to be judged
 *       by.</li>
 *   <li><b>An unrecognised value is refused, not ignored.</b> Ignoring it would send the request to
 *       the POST handler — so a typo, or a header the firewall mangled, would quietly perform a
 *       different operation from the one asked for. This codebase has been bitten by a fallback
 *       meaning "behave as before" hiding a missing wire; here the safe-looking fallback is the
 *       dangerous one.</li>
 * </ol>
 *
 * <h2>Multipart still works</h2>
 * Several overridden routes consume {@code multipart/form-data} ({@code PUT /assets/repairs/{id}},
 * {@code PUT /assets/image/{id}}). Tomcat parses parts for POST requests, and the wrapper below
 * changes only what {@code getMethod()} reports — {@code getParts()} delegates down to the real
 * request, which is still a POST on the wire. That is reasoning about someone else's container, so
 * the multipart cases are covered by their own tests rather than assumed.
 */
@Slf4j
public class HttpMethodOverrideFilter extends OncePerRequestFilter {

    /** The header the front end sends. Matches what the firewall and the SMS gateway already use. */
    public static final String HEADER = "X-HTTP-Method-Override";

    /** Exactly the verbs the firewall blocks, and nothing else. */
    private static final Set<String> OVERRIDABLE = Set.of("PUT", "DELETE");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String requested = request.getHeader(HEADER);

        if (requested == null || requested.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String override = requested.trim().toUpperCase(Locale.ROOT);

        /*
         * An override on anything but POST is refused rather than ignored.
         *
         * A GET carrying "DELETE" is either a confused client or someone probing for exactly the
         * escalation this filter's ordering exists to prevent. Neither should be answered by quietly
         * serving the GET.
         */
        if (!HttpMethod.POST.matches(request.getMethod())) {
            refuse(response, "The " + HEADER + " header is only accepted on POST requests.");
            return;
        }

        if (!OVERRIDABLE.contains(override)) {
            refuse(response, "'" + requested + "' is not an overridable method. "
                    + HEADER + " accepts PUT or DELETE.");
            return;
        }

        log.debug("Treating POST {} as {} ({} header)", request.getRequestURI(), override, HEADER);
        chain.doFilter(new MethodOverridingRequest(request, override), response);
    }

    /**
     * Written by hand because this runs before the dispatcher.
     *
     * <p>There is no {@code @ExceptionHandler} in reach here and no {@code ProblemDetail} writer, so
     * the body is assembled directly — in the same shape the rest of the application's errors use, so
     * the front end's existing reader finds the message where it expects it.
     */
    private void refuse(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"status\":400,\"title\":\"Bad Request\",\"errorCode\":\"INVALID_METHOD_OVERRIDE\","
                        + "\"detail\":\"" + detail.replace("\"", "'") + "\"}");
    }

    /**
     * Reports the overridden verb and delegates everything else.
     *
     * <p>Both {@code RequestMappingHandlerMapping} and Spring Security's request matchers read
     * {@code getMethod()}, which is why overriding that one method is enough for the whole stack to
     * agree — and why this filter's position in the chain decides whether they agree <em>before</em>
     * authorization or after it.
     */
    private static final class MethodOverridingRequest extends HttpServletRequestWrapper {

        private final String method;

        private MethodOverridingRequest(HttpServletRequest request, String method) {
            super(request);
            this.method = method;
        }

        @Override
        public String getMethod() {
            return method;
        }
    }
}
