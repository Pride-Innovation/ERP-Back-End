/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pride.bank.erp.exceptionEntryPoint.CustomAuthenticationEntryPointHandler;
import pride.bank.erp.exceptionEntryPoint.CustomBearerTokenAccessDeniedHandler;

import java.util.List;

import static pride.bank.erp.helper.Constants.*;


@Configuration
@EnableWebSecurity
// Without this, every @PreAuthorize in the codebase is silently ignored — the annotation compiles,
// reads as protection, and enforces nothing. Enabling it activates them; no endpoint changes
// behavior except the ones that carry an annotation.
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final CustomBearerTokenAccessDeniedHandler customBearerTokenAccessDeniedHandler;
    private final CustomAuthenticationEntryPointHandler customAuthenticationEntryPointHandler;
    private final LogoutService logoutService;

    @Value("${front-end.environment}")
    private String environment;

    /**
     * Configures the application's security filter chain using Spring Security.
     * This includes route-based authorization, JWT authentication, exception handling,
     * and session management policies.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request ->
                        request.requestMatchers(
                                        AUTH_ROUTE,
                                        REFRESH_TOKEN_ROUTE,
                                        VERIFY_EMAIL,
                                        REQUEST_PASSWORD_RESET,
                                        RESET_PASSWORD,
                                        "/swagger-ui/**",
                                        "/swagger-ui.html",
                                        "/v3/api-docs/**",
                                        "/v3/api-docs",
                                        "/ws/**",  // WebSocket endpoint
                                        // The handheld scanner's two calls. The PDA is not signed in:
                                        // the one-time pairing code, then the relay token it buys,
                                        // are the authorisation - and they authorise only text into
                                        // one browser's search box. See ScanRelayService.
                                        "/api/v1/scan-relay/**",
                                        // The container ERROR dispatch re-enters the filter chain with no
                                        // authentication (stateless). Without permitting /error, every
                                        // controller error (400/500) is masked as a 403 "Full authentication
                                        // is required" instead of surfacing the real status and message.
                                        "/error"
                                )
                                .permitAll()
                                // User Routes
                                /*
                                 * These three come BEFORE the generic /users/** POST matcher, and the
                                 * order is the whole point: the first match wins, which is how a
                                 * role holding only REJECT_REQUEST once found itself unable to
                                 * reject. Put after, they would never be consulted.
                                 */
                                .requestMatchers(HttpMethod.GET, OWN_PROFILE_ROUTE, STAFF_PICKER_ROUTE)
                                .authenticated()
                                .requestMatchers(HttpMethod.POST, CHANGE_PASSWORD_ROUTE).authenticated()
                                .requestMatchers(HttpMethod.POST,
                                        USER_ACCESS_ROUTES, USER_ACCESS_ENABLE, USER_ACCESS_BLOCK,
                                        USER_ACCESS_UNBLOCK, USER_ACCESS_LEAVE)
                                .hasAuthority(MANAGE_USER_ACCESS)
                                .requestMatchers(HttpMethod.PUT,
                                        USER_IMAGE_ROUTE, USER_IMAGE_REMOVE_ROUTE)
                                .authenticated()
                                .requestMatchers(HttpMethod.POST, USER_ROUTE).hasAuthority(CREATE_USER)
                                .requestMatchers(HttpMethod.GET, USER_ROUTE).hasAuthority(READ_USER)
                                .requestMatchers(HttpMethod.DELETE, USER_ROUTE).hasAuthority(DELETE_USER)
                                .requestMatchers(HttpMethod.PUT, USER_ROUTE).hasAuthority(UPDATE_USER)
                                .requestMatchers(HttpMethod.POST, REGISTER_USER_ROUTE).hasAuthority(CREATE_USER)
                                // Role Routes
                                .requestMatchers(HttpMethod.POST, ROLE_ROUTE).hasAuthority(CREATE_ROLE)
                                .requestMatchers(HttpMethod.GET, ROLE_ROUTE).hasAuthority(READ_ROLE)
                                .requestMatchers(HttpMethod.DELETE, ROLE_ROUTE).hasAuthority(DELETE_ROLE)
                                .requestMatchers(HttpMethod.PUT, ROLE_ROUTE).hasAuthority(UPDATE_ROLE)
                                // Permission Routes
                                .requestMatchers(HttpMethod.POST, PERMISSION_ROUTE).hasAuthority(CREATE_PERMISSION)
                                .requestMatchers(HttpMethod.GET, PERMISSION_ROUTE).hasAuthority(READ_PERMISSION)
                                .requestMatchers(HttpMethod.DELETE, PERMISSION_ROUTE).hasAuthority(DELETE_PERMISSION)
                                .requestMatchers(HttpMethod.PUT, PERMISSION_ROUTE).hasAuthority(UPDATE_PERMISSION)
                                // Request Routes
                                /*
                                 * The two dashboard request widgets, declared before the general
                                 * /requests/** rule below so the more specific match wins.
                                 *
                                 * They accept the dashboard permission as well as READ_REQUEST,
                                 * because the dashboard axis is meant to stand on its own: someone
                                 * granted DASH_VIEW_REQUESTS to see their queue should not also need
                                 * the permission that opens the full requests listing.
                                 */
                                .requestMatchers(HttpMethod.GET, GENERAL_ROUTE + "/requests/awaiting-me")
                                .hasAnyAuthority(READ_REQUEST, DASH_VIEW_REQUESTS)
                                .requestMatchers(HttpMethod.GET, GENERAL_ROUTE + "/requests/open")
                                .hasAnyAuthority(READ_REQUEST, DASH_VIEW_REQUESTS)
                                .requestMatchers(HttpMethod.GET, REQUEST_ROUTE).hasAuthority(READ_REQUEST)
                                .requestMatchers(HttpMethod.POST, REQUEST_ROUTE).hasAuthority(CREATE_REQUEST)
                                .requestMatchers(HttpMethod.PUT, REQUEST_ROUTE).hasAuthority(UPDATE_REQUEST)
                                .requestMatchers(HttpMethod.DELETE, REQUEST_ROUTE).hasAuthority(DELETE_REQUEST)
                                // Status Routes
                                .requestMatchers(HttpMethod.POST, STATUS_ROUTE).hasAuthority(CREATE_STATUS)
                                .requestMatchers(HttpMethod.DELETE, STATUS_ROUTE).hasAuthority(DELETE_STATUS)
                                .requestMatchers(HttpMethod.PUT, STATUS_ROUTE).hasAuthority(UPDATE_STATUS)
                                // Inventory / Stock Routes
                                .requestMatchers(HttpMethod.POST, INVENTORY_ROUTE).hasAuthority(CREATE_INVENTORY)
                                .requestMatchers(HttpMethod.DELETE, INVENTORY_ROUTE).hasAuthority(DELETE_INVENTORY)
                                .requestMatchers(HttpMethod.PUT, INVENTORY_ROUTE).hasAuthority(UPDATE_INVENTORY)
                                .requestMatchers(HttpMethod.GET, INVENTORY_ROUTE).hasAuthority(READ_INVENTORY)
                                // Asset Routes.
                                // The self-scoped "my assets" view is matched FIRST — matchers are
                                // evaluated in order, and /assets/** would otherwise swallow it and
                                // demand READ_ASSET. That permission governs the branch-wide asset
                                // register; requiring it to see the items you personally hold would
                                // mean only administrators could check their own accountability.
                                // The handler takes no id and reads the holder off the JWT, so
                                // authentication alone is the correct and sufficient guard here.
                                .requestMatchers(HttpMethod.GET, MY_ASSETS_ROUTE).authenticated()
                                /*
                                 * Asset lifecycle actions, ahead of the verb rules below.
                                 *
                                 * The verb rules alone gave every POST under /assets/** the same
                                 * permission, so registering an asset, reassigning one to another
                                 * officer and importing a spreadsheet of several thousand were all
                                 * CREATE_ASSET; handing an asset into a store was UPDATE_ASSET,
                                 * indistinguishable from correcting a typo in its model number.
                                 * Matchers are evaluated in order, so these must precede them.
                                 */
                                .requestMatchers(HttpMethod.POST, ASSET_REASSIGN_ROUTE).hasAuthority(REASSIGN_ASSET)
                                // Booking an asset in for repair and closing that repair off are the
                                // same duty; reading the repair history is not.
                                .requestMatchers(HttpMethod.POST, ASSET_REPAIR_ROUTE).hasAuthority(REPAIR_ASSET)
                                .requestMatchers(HttpMethod.PUT, ASSET_REPAIR_ROUTE).hasAuthority(REPAIR_ASSET)
                                // Reading the maintenance record is a different duty from doing
                                // the repair, and routinely a different person. The branch scope
                                // applies on top, inside RepairService.
                                .requestMatchers(HttpMethod.GET, ASSET_REPAIR_ROUTE).hasAuthority(READ_REPAIR_HISTORY)
                                .requestMatchers(HttpMethod.PUT, ASSET_TO_STORE_ROUTE).hasAuthority(RECEIVE_ASSET_IN_STORE)
                                // The template describes what an import may contain, so it answers
                                // to the same permission as the import itself.
                                .requestMatchers(HttpMethod.POST, ASSET_BULK_INSERT_ROUTE).hasAuthority(IMPORT_ASSET)
                                .requestMatchers(HttpMethod.GET, ASSET_IMPORT_TEMPLATE_ROUTE).hasAuthority(IMPORT_ASSET)
                                /*
                                 * Per-category counts feeding the "Assets by Category" and "Asset
                                 * Condition" widgets. Accepts the dashboard authority as well as
                                 * READ_ASSET, matching the two request widgets above: someone
                                 * granted DASH_VIEW_ASSETS to see the estate summarized should not
                                 * also need the permission that opens the full register. Without
                                 * this the GET rule below demanded READ_ASSET and the dashboard
                                 * rendered a widget its own endpoint then refused.
                                 */
                                .requestMatchers(HttpMethod.GET, ASSET_STATISTICS_ROUTE)
                                .hasAnyAuthority(READ_ASSET, DASH_VIEW_ASSETS)
                                .requestMatchers(HttpMethod.POST, ASSET_ROUTE).hasAuthority(CREATE_ASSET)
                                .requestMatchers(HttpMethod.DELETE, ASSET_ROUTE).hasAuthority(DELETE_ASSET)
                                .requestMatchers(HttpMethod.PUT, ASSET_ROUTE).hasAuthority(UPDATE_ASSET)
                                .requestMatchers(HttpMethod.GET, ASSET_ROUTE).hasAuthority(READ_ASSET)
                                /*
                                 * Disposal. Registered here rather than with the asset routes
                                 * because it is a movement — POST /movements/disposal raises a
                                 * DISPOSAL_TRANSFER and enforces the store and useful-life rules.
                                 * Nothing under /movements/** matched any rule, so it reached
                                 * `.anyRequest().authenticated()` and any signed-in user could
                                 * write an asset off. The preview is read-only but names the same
                                 * asset and destination, so it answers to the same permission.
                                 */
                                .requestMatchers(HttpMethod.GET, MOVEMENT_DISPOSAL_PREVIEW_ROUTE).hasAuthority(DISPOSE_ASSET)
                                .requestMatchers(HttpMethod.POST, MOVEMENT_DISPOSAL_ROUTE).hasAuthority(DISPOSE_ASSET)
                                /*
                                 * The register in bulk. This returns every asset of a category in
                                 * one unpaginated list and matched no rule, so it was reachable
                                 * with any valid token — a way around READ_ASSET rather than an
                                 * extension of it. EXPORT_ASSET is deliberately its own permission:
                                 * reading the register a page at a time on screen and walking out
                                 * with the whole of it are different acts, which is why one is
                                 * audited and the other is not.
                                 */
                                .requestMatchers(HttpMethod.GET, EXPORT_ASSETS_ROUTE).hasAuthority(EXPORT_ASSET)
                                // Dashboard reporting routes.
                                // Each mirrors the authority the dashboard uses to decide whether to
                                // render the widget fed by it, so the API and the UI agree on who may
                                // see what. Without these the reports matched no rule and fell through
                                // to `.anyRequest().authenticated()`.
                                /*
                                 * The national asset roll-up: any of the three ways of being at ALL
                                 * scope for assets.
                                 *
                                 * This named only VIEW_ALL_BRANCHES, which predates the multiplier
                                 * model. The dashboard renders the "Assets Across All Branches"
                                 * widget whenever the viewer's scope resolves to ALL — and a unit
                                 * confers that through DASH_SCOPE_ALL and VIEW_ALL_BRANCH_ASSETS,
                                 * never through the legacy permission. So an Admin Unit officer got
                                 * the widget on screen and a 403 from the endpoint behind it: the
                                 * exact drift between the two halves that CLAUDE.md warns about.
                                 *
                                 * All three are listed rather than one replacing another. The legacy
                                 * permission stays so nobody who could see the bank yesterday is
                                 * narrowed today; DASH_SCOPE_ALL is the dashboard axis and this is a
                                 * dashboard endpoint; VIEW_ALL_BRANCH_ASSETS is the asset-view
                                 * multiplier and this is asset data.
                                 */
                                .requestMatchers(HttpMethod.GET, REPORT_ASSETS_BY_BRANCH)
                                .hasAnyAuthority(VIEW_ALL_BRANCHES, DASH_SCOPE_ALL, VIEW_ALL_BRANCH_ASSETS)
                                .requestMatchers(HttpMethod.GET, REPORT_ALL_BRANCH_ASSETS)
                                .hasAnyAuthority(VIEW_ALL_BRANCHES, DASH_SCOPE_ALL, VIEW_ALL_BRANCH_ASSETS)
                                /*
                                 * The dashboard subject permissions belong here too.
                                 *
                                 * Granting DASH_VIEW_STOCK is meant to be enough to see stock
                                 * summarized without also opening the store pages — that separation
                                 * is the whole reason the axis exists. While these named only the
                                 * READ_* permissions, the widget rendered on the subject grant and
                                 * its endpoint refused. ASSET_STATISTICS_ROUTE above already lists
                                 * both; these two were missed.
                                 */
                                .requestMatchers(HttpMethod.GET, REPORT_MONTHLY_STOCK)
                                .hasAnyAuthority(READ_STORE, READ_INVENTORY, DASH_VIEW_STOCK)
                                .requestMatchers(HttpMethod.GET, REPORT_REQUEST_SUMMARY)
                                .hasAnyAuthority(READ_REQUEST, DASH_VIEW_REQUESTS)
                                // Requests approval and Acknowledgements
                                /*
                                 * One rule, not two.
                                 *
                                 * This was two matchers for the same method and path — APPROVE_REQUEST
                                 * then REJECT_REQUEST. The first match wins, so the second was
                                 * unreachable and a role granted only REJECT_REQUEST could not reject:
                                 * the filter chain refused it before the handler was ever consulted.
                                 * Latent while MANAGER holds both, and live the moment anyone
                                 * configures a reject-only role in Settings.
                                 *
                                 * Which of the two acts is being performed is decided by the payload
                                 * and enforced downstream; this gate only asks whether the caller has
                                 * business at an approval step at all.
                                 */
                                .requestMatchers(HttpMethod.POST, REQUEST_APPROVAL)
                                .hasAnyAuthority(APPROVE_REQUEST, REJECT_REQUEST)
                                .requestMatchers(HttpMethod.POST, REQUEST_ISSUANCE).hasAuthority(ISSUE_ITEMS)
                                .requestMatchers(HttpMethod.POST, REQUEST_ACKNOWLEDGEMENT).hasAuthority(ACKNOWLEDGE_REQUEST)
                                .requestMatchers(HttpMethod.POST, ISSUANCE_APPROVAL).hasAuthority(APPROVE_ISSUANCE)

                                // Audit — the activity log, the Envers revision views, and the
                                // endpoint reports call to record an export.
                                //
                                // This route used to sit in the permitAll block above, marked "for
                                // test purpose". That made every recorded action in the bank — with
                                // the actor and their IP address — readable by anyone who could
                                // reach the host, without a token. Authentication is the floor;
                                // restricting it to an audit authority belongs with the wider
                                // permissions pass.
                                .requestMatchers(AUDIT_ROUTE).authenticated()

                                .anyRequest()
                                .authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling
                                .authenticationEntryPoint(customAuthenticationEntryPointHandler)
                                .accessDeniedHandler(customBearerTokenAccessDeniedHandler)
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                /*
                 * The overridden verb is restored here: after CORS, before authorization.
                 *
                 * Both halves of that sentence are load-bearing, and getting the first one wrong is
                 * what broke every write in development.
                 *
                 * BEFORE AUTHORIZATION, because authorization is method-specific - 17 rules below
                 * name HttpMethod.PUT or DELETE, and the same path routinely carries a different
                 * permission per verb. Judged as the POST it arrived as, a holder of CREATE_ASSET
                 * would pass the POST rule and land on the @DeleteMapping.
                 *
                 * AFTER CORS, because CORS is about what the *browser actually sent*. The filter was
                 * first registered as a servlet filter at HIGHEST_PRECEDENCE, ahead of the whole
                 * chain - so DefaultCorsProcessor saw a method of "PUT" on a request the browser had
                 * sent as POST, found PUT absent from allowedMethods, and answered
                 * "Reject: HTTP 'PUT' is not allowed" with a 403. The preflight had already
                 * succeeded, because that one correctly asks about POST; only the real request
                 * failed, which is why it looked like a server fault rather than a CORS rule.
                 *
                 * Widening allowedMethods to include PUT and DELETE would also have silenced it, and
                 * would have been the wrong fix twice over: it would make the application advertise
                 * `Access-Control-Allow-Methods: PUT, DELETE` to browsers - verbs the firewall drops -
                 * and it would leave the CORS layer adjudicating a verb that never crossed the
                 * network.
                 */
                .addFilterBefore(new HttpMethodOverrideFilter(),
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .logout(logout ->
                        logout
                                .logoutUrl(LOGOUT)
                                .addLogoutHandler(logoutService)
                                .logoutSuccessHandler(
                                        (request, response, authentication) -> SecurityContextHolder.clearContext()
                                )
                );

        return http.build();
    }

    /**
     * Cross-origin rules — which matter in development and, in production, must not be needed at all.
     *
     * <h2>The production constraint, stated here because this is where someone will look</h2>
     * The firewall <b>blocks OPTIONS</b>, and a CORS preflight <em>is</em> an OPTIONS request. This
     * application already forces a preflight on every single call — it sends {@code Authorization}
     * and {@code application/json}, neither of which is CORS-safe-listed — so behind the firewall a
     * cross-origin deployment cannot work at all, and could not before this change either.
     *
     * <p>The front end must therefore be served from the <b>same origin</b> as the API in production
     * (one host, one scheme, one port, with a reverse proxy sending {@code /api/v1/**} here). Nothing
     * below then runs, because same-origin requests are never preflighted. Adding the two headers
     * costs nothing extra for the same reason.
     *
     * <p>This configuration stays for local development, where the front end runs on :3000 against a
     * backend on :7777 with no firewall in between.
     */
    /**
     * The CSRF header the firewall requires to be present on state-changing requests.
     *
     * <h2>What this is, and what it is not</h2>
     * The firewall rejects a POST that carries no CSRF token, so one has to be sent. It does
     * <b>not</b> validate the token against a cookie, and neither does this application — CSRF stays
     * {@code disable()}d below and nothing here reads the value.
     *
     * <p>That is not a gap. This API authenticates with a Bearer token held in the browser's
     * {@code sessionStorage}, never a cookie, so a cross-site request cannot attach credentials in
     * the first place — the attack the token would defend against is already impossible. Turning on
     * stateful CSRF validation would add a session to a deliberately {@code STATELESS} service to
     * protect against something it is not exposed to.
     *
     * <p>Named here so the CORS whitelist and the front end cannot drift on the spelling; a mismatch
     * would surface as a preflight failure naming no header.
     */
    public static final String CSRF_TOKEN_HEADER = "X-CSRF-TOKEN";

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(environment));
        /*
         * GET and POST, because those are the only two verbs that reach this service.
         *
         * The firewall accepts nothing else. PUT and DELETE are gone from this list not because the
         * server refuses them - it still serves them, and a direct API client inside the firewall may
         * still use them - but because this list is what a *browser* is told it may send, and a
         * browser that believes it may send a PUT will send one and have it blocked. Advertising a
         * verb the network will drop is how a working page becomes an unexplained failure.
         *
         * OPTIONS is not listed either, and never needed to be: this list is checked against
         * `Access-Control-Request-Method`, which names the verb the preflight is *asking about*, not
         * the preflight's own verb. Listing it only suggested the application expects OPTIONS traffic,
         * which it must not - see the note above about why preflight cannot happen in production at
         * all.
         */
        configuration.setAllowedMethods(List.of("GET", "POST"));
        /*
         * This is a whitelist, not a default-open list: a header absent from it is rejected at
         * preflight, with a CORS error that says nothing about which header was at fault. Both of
         * the new ones have to be named or every write fails in development.
         */
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                HttpMethodOverrideFilter.HEADER,
                CSRF_TOKEN_HEADER,
                // The double-submit header, matching the SMS gateway's wire format. Only consulted
                // in local development: production is same-origin, so no preflight ever happens.
                "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
