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
// behaviour except the ones that carry an annotation.
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
                                        // The container ERROR dispatch re-enters the filter chain with no
                                        // authentication (stateless). Without permitting /error, every
                                        // controller error (400/500) is masked as a 403 "Full authentication
                                        // is required" instead of surfacing the real status and message.
                                        "/error"
                                )
                                .permitAll()
                                // User Routes
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
                                .requestMatchers(HttpMethod.POST, ASSET_ROUTE).hasAuthority(CREATE_ASSET)
                                .requestMatchers(HttpMethod.DELETE, ASSET_ROUTE).hasAuthority(DELETE_ASSET)
                                .requestMatchers(HttpMethod.PUT, ASSET_ROUTE).hasAuthority(UPDATE_ASSET)
                                .requestMatchers(HttpMethod.GET, ASSET_ROUTE).hasAuthority(READ_ASSET)
                                // Dashboard reporting routes.
                                // Each mirrors the authority the dashboard uses to decide whether to
                                // render the widget fed by it, so the API and the UI agree on who may
                                // see what. Without these the reports matched no rule and fell through
                                // to `.anyRequest().authenticated()`.
                                .requestMatchers(HttpMethod.GET, REPORT_ASSETS_BY_BRANCH).hasAuthority(VIEW_ALL_BRANCHES)
                                .requestMatchers(HttpMethod.GET, REPORT_ALL_BRANCH_ASSETS).hasAuthority(VIEW_ALL_BRANCHES)
                                .requestMatchers(HttpMethod.GET, REPORT_MONTHLY_STOCK).hasAnyAuthority(READ_STORE, READ_INVENTORY)
                                .requestMatchers(HttpMethod.GET, REPORT_PENDING_REQUESTS).hasAuthority(READ_REQUEST)
                                .requestMatchers(HttpMethod.GET, REPORT_REQUEST_SUMMARY).hasAuthority(READ_REQUEST)
                                // Requests approval and Acknowledgements
                                .requestMatchers(HttpMethod.POST, REQUEST_APPROVAL).hasAuthority(APPROVE_REQUEST)
                                .requestMatchers(HttpMethod.POST, REQUEST_APPROVAL).hasAuthority(REJECT_REQUEST)
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(environment));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
