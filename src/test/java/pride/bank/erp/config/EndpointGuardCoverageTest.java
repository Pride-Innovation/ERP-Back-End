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
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fails when an endpoint answers to nothing but "be signed in".
 *
 * <p>Every gap this permissions pass has closed was the same shape: a route nobody remembered to
 * add a rule for, which therefore fell through to {@code .anyRequest().authenticated()} and worked
 * perfectly for everyone. Disposal sat open that way for months. Nothing failed, because nothing
 * was asking — an unguarded endpoint is indistinguishable from a guarded one until someone who
 * should not have reached it does.
 *
 * <p>This is the thing that asks. It walks every request-mapped handler in the application and
 * checks each is covered by either a {@code @PreAuthorize} or a URL matcher.
 *
 * <h2>How coverage is decided</h2>
 *
 * <p>A method carrying {@code @PreAuthorize} (its own or its controller's) is guarded, by
 * inspection. Everything else is <b>probed</b>: the request is issued as a signed-in user holding no
 * authorities at all, and a 403 means some matcher refused it.
 *
 * <p>The two halves are not interchangeable. Matcher rules live in the filter chain and run before
 * the DispatcherServlet, so their refusal always arrives; {@code @PreAuthorize} is a method
 * interceptor that runs *after* argument resolution, so an endpoint with a required parameter would
 * answer 400 before its guard was ever consulted and probing alone would report it as open.
 *
 * <h2>Why there is a list of known gaps</h2>
 *
 * <p>{@link #KNOWN_UNGUARDED} is the work not yet done — Settings reference data and approval
 * workflows, per {@code docs/PERMISSIONS-PLAN.md}. The test asserts the gap set matches it
 * <b>exactly</b>, in both directions: a new unguarded endpoint fails the build, and so does a listed
 * one that has since been guarded but left in the list. The second half is what stops the list
 * quietly becoming a graveyard that excuses everything. It should only ever shrink.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EndpointGuardCoverageTest {

    /**
     * Endpoints that are meant to answer to authentication alone, with the reason.
     *
     * <p>Distinct from {@link #KNOWN_UNGUARDED}: nothing here is outstanding work.
     */
    private static final Set<String> DELIBERATELY_AUTHENTICATED_ONLY = Set.of(
            // Resolves the holder from the JWT and takes no id, so it can only return the caller's
            // own records. Requiring READ_ASSET — which governs the branch-wide register — would
            // mean only administrators could check what they are personally accountable for.
            "GET /api/v1/assets/my-assets",
            // The caller's own notifications. Every one of these resolves the user from the JWT and
            // acts only on their own records, so authentication is not a weaker guard here — it is
            // the whole of the question being asked.
            "GET /api/v1/notifications",
            "GET /api/v1/notifications/unread-count",
            "PUT /api/v1/notifications/{id}/read",
            "PUT /api/v1/notifications/read-all"
    );

    /**
     * The remaining gap, as at 2026-08-27. Every line here is a route any signed-in user can reach.
     *
     * <p>Populated from a run of this test, not by hand. Delete lines as they are guarded — the
     * assertion below fails if a line here is guarded and left in place, so the list cannot rot.
     */
    private static final Set<String> KNOWN_UNGUARDED = Set.of(
            // Settings reference data — plan Phase 2. Reuse the SETTING keys: one section, one set of keys,
            // rather than eleven near-identical triples.
            "DELETE /api/v1/asset-types/{id}",
            "DELETE /api/v1/branches/{id}",
            "DELETE /api/v1/commodities/{id}",
            "DELETE /api/v1/consultants/{id}",
            "DELETE /api/v1/couriers/{id}",
            "DELETE /api/v1/departments/{id}",
            "DELETE /api/v1/districts/{id}",
            "DELETE /api/v1/regions/{id}",
            "DELETE /api/v1/suppliers/{id}",
            "DELETE /api/v1/titles/{id}",
            "DELETE /api/v1/units/{id}",
            "GET /api/v1/asset-types",
            "GET /api/v1/asset-types/{id}",
            "GET /api/v1/branches",
            "GET /api/v1/branches/{id}",
            "GET /api/v1/commodities",
            "GET /api/v1/commodities/{id}",
            "GET /api/v1/consultants",
            "GET /api/v1/consultants/{id}",
            "GET /api/v1/couriers",
            "GET /api/v1/couriers/{id}",
            "GET /api/v1/departments",
            "GET /api/v1/departments/{id}",
            "GET /api/v1/districts",
            "GET /api/v1/districts/{id}",
            "GET /api/v1/regions",
            "GET /api/v1/regions/{id}",
            "GET /api/v1/suppliers",
            "GET /api/v1/suppliers/{id}",
            "GET /api/v1/titles",
            "GET /api/v1/titles/{id}",
            "GET /api/v1/units",
            "GET /api/v1/units/{id}",
            "POST /api/v1/asset-types",
            "POST /api/v1/branches",
            "POST /api/v1/commodities",
            "POST /api/v1/consultants",
            "POST /api/v1/couriers",
            "POST /api/v1/departments",
            "POST /api/v1/districts",
            "POST /api/v1/regions",
            "POST /api/v1/suppliers",
            "POST /api/v1/titles",
            "POST /api/v1/units",
            "PUT /api/v1/asset-types/{id}",
            "PUT /api/v1/asset-types/{id}/custom-attributes",
            "PUT /api/v1/asset-types/{id}/field-config",
            "PUT /api/v1/branches/{id}",
            "PUT /api/v1/commodities/{id}",
            "PUT /api/v1/consultants/{id}",
            "PUT /api/v1/couriers/{id}",
            "PUT /api/v1/departments/{id}",
            "PUT /api/v1/districts/{id}",
            "PUT /api/v1/regions/{id}",
            "PUT /api/v1/suppliers/{id}",
            "PUT /api/v1/titles/{id}",
            "PUT /api/v1/units/{id}",

            // Approval workflows — plan Phase 3. Editing one changes who approves what, bank-wide.
            "DELETE /api/v1/approval-workflows/{id}",
            "GET /api/v1/approval-workflows",
            "GET /api/v1/approval-workflows/resolve",
            "GET /api/v1/approval-workflows/{id}",
            "GET /api/v1/workflow/step-logs/{requestId}",
            "POST /api/v1/approval-workflows",
            "PUT /api/v1/approval-workflows/{id}",
            "PUT /api/v1/approval-workflows/{id}/toggle-active",

            // Issuance, GRN and reconciliation — plan Phase 3. The writes are matched; the reads are not.
            "GET /api/v1/acknowledge-issuance/{requestId}",
            "GET /api/v1/acknowledge-request/{requestId}",
            "GET /api/v1/approve-issuance/{requestId}",
            "GET /api/v1/grn-commodities/{stockID}",
            "GET /api/v1/grn-reports/{grnReportId}/document",
            "GET /api/v1/issuance/{requestId}",
            "GET /api/v1/last-issued/{commodityId}",
            "GET /api/v1/stock-reconciliation",
            "POST /api/v1/acknowledge-issuance",
            "POST /api/v1/delete-stock/{stockID}",
            "POST /api/v1/upload-grn/{grnReportId}",
            "PUT /api/v1/download-grn/{grnReportId}",

            // Inventory and store. INVENTORY_ROUTE matches /stocks/**, so nothing under /inventory/** is
            // covered by it — a gap the route name hides rather than advertises.
            "GET /api/v1/assignment-history",
            "GET /api/v1/inventory/assets",
            "GET /api/v1/inventory/assets/disposable",
            "GET /api/v1/inventory/assets/temporary-pool",
            "GET /api/v1/inventory/branch-overview",
            "GET /api/v1/inventory/in-transit",
            "GET /api/v1/inventory/low-stock",
            "GET /api/v1/inventory/my-items",
            "GET /api/v1/inventory/stores/{storeId}/assets",
            "GET /api/v1/statuses",
            "GET /api/v1/store",

            // Reporting. Each returns aggregates across the estate, so each wants the read permission of
            // whatever it summarises.
            "GET /api/v1/it-and-office/annual-stock",
            "GET /api/v1/it-and-office/monthly-stock",
            "GET /api/v1/it-and-office/monthly-stock/summary",
            "GET /api/v1/leave",
            "GET /api/v1/monthly-itAndOffice-stats",
            "GET /api/v1/request-rating-stats",
            "GET /api/v1/request-report/{requestId}",
            "GET /api/v1/request-stats-one-year",
            "GET /api/v1/stationery-report",
            "GET /api/v1/stationery-report/branch",
            "GET /api/v1/stationery/monthly-request",
            "GET /api/v1/stationery/monthly-stock",
            "POST /api/v1/leave/{userId}",

            // Bulk extracts — the same hole EXPORT_ASSET closed for assets, still open for these two.
            "GET /api/v1/export/inventory",
            "GET /api/v1/export/request",

            // The activity log. SecurityConfiguration matches AUDIT_ROUTE to .authenticated() and says in
            // place that narrowing it to an audit authority belongs to this pass. READ_AUDIT exists.
            "GET /api/v1/audit/requests",
            "GET /api/v1/audit/roles",
            "GET /api/v1/audit/trails",
            "GET /api/v1/audit/trails/summary",
            "GET /api/v1/audit/users",
            "POST /api/v1/audit/exports"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    /**
     * Paths the security configuration deliberately opens to everyone: login, token refresh,
     * password reset, the API docs, the websocket handshake, and the container's ERROR dispatch.
     */
    private static boolean isPublic(String path) {
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/request-password-reset")
                || path.startsWith("/api/v1/reset-password")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/ws")
                || path.equals("/error");
    }

    private static boolean carriesMethodSecurity(HandlerMethod handler) {
        return handler.getMethodAnnotation(PreAuthorize.class) != null
                || handler.getBeanType().getAnnotation(PreAuthorize.class) != null;
    }

    /**
     * A concrete URL for a mapping template.
     *
     * <p>Path variables become an id far beyond anything seeded, so a handler that does run — which
     * only happens when it is unguarded, and is exactly what this test is reporting — finds nothing
     * to act on. Belt and braces with the rollback on the test itself.
     */
    private static String concreteUrl(String pattern) {
        return pattern.replaceAll("\\{[^}]+}", "999999999").replace("/**", "/999999999");
    }

    @Test
    @Transactional
    @WithMockUser(authorities = {})
    void everyEndpointIsCoveredByAPermissionRule() {
        Set<String> unguarded = new TreeSet<>();

        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();

            Set<String> patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues()
                    : Set.of();
            var methods = info.getMethodsCondition().getMethods();
            if (patterns.isEmpty() || methods.isEmpty()) continue;

            for (String pattern : patterns) {
                if (isPublic(pattern)) continue;
                for (var requestMethod : methods) {
                    String signature = requestMethod.name() + " " + pattern;
                    if (DELIBERATELY_AUTHENTICATED_ONLY.contains(signature)) continue;
                    if (carriesMethodSecurity(handler)) continue;
                    if (!refusedWithNoAuthorities(requestMethod.name(), pattern)) {
                        unguarded.add(signature);
                    }
                }
            }
        }

        assertThat(unguarded)
                .as("""
                        Endpoints reachable by any signed-in user.

                        If this lists something new, give it a rule — a @PreAuthorize on the method, \
                        or a matcher in SecurityConfiguration — rather than adding it below.
                        If something previously listed no longer appears, delete it from \
                        KNOWN_UNGUARDED: that list is the remaining work and is meant to shrink.""")
                .containsExactlyInAnyOrderElementsOf(KNOWN_UNGUARDED);
    }

    /** True when the filter chain refuses the request outright for a caller holding no authorities. */
    private boolean refusedWithNoAuthorities(String method, String pattern) {
        String url = concreteUrl(pattern);
        try {
            MockHttpServletRequestBuilder request = switch (HttpMethod.valueOf(method).name()) {
                case "GET" -> MockMvcRequestBuilders.get(url);
                case "POST" -> MockMvcRequestBuilders.post(url).contentType("application/json").content("{}");
                case "PUT" -> MockMvcRequestBuilders.put(url).contentType("application/json").content("{}");
                case "DELETE" -> MockMvcRequestBuilders.delete(url);
                case "PATCH" -> MockMvcRequestBuilders.patch(url).contentType("application/json").content("{}");
                default -> null;
            };
            if (request == null) return true; // OPTIONS/HEAD — not an application surface
            return mockMvc.perform(request).andReturn().getResponse().getStatus() == 403;
        } catch (Exception handlerFailed) {
            // The handler ran and blew up, which means the chain admitted the request.
            return false;
        }
    }
}
