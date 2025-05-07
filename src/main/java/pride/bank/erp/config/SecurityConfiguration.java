package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final CustomBearerTokenAccessDeniedHandler customBearerTokenAccessDeniedHandler;
    private final CustomAuthenticationEntryPointHandler customAuthenticationEntryPointHandler;
    private final LogoutService logoutService;

    @Value("${front-end.environment}")
    private String environment;

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
                                        AUDIT_ROUTE // Audit route for test purpose
                                )
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, USER_ROUTE).hasAuthority(CREATE_USER)
                                .requestMatchers(HttpMethod.GET, USER_ROUTE).hasAuthority(READ_USER)
                                .requestMatchers(HttpMethod.DELETE, USER_ROUTE).hasAuthority(DELETE_USER)
                                .requestMatchers(HttpMethod.PUT, USER_ROUTE).hasAuthority(UPDATE_USER)
                                .requestMatchers(HttpMethod.POST, ROLE_ROUTE).hasAuthority(CREATE_ROLE)
                                .requestMatchers(HttpMethod.GET, ROLE_ROUTE).hasAuthority(READ_ROLE)
                                .requestMatchers(HttpMethod.DELETE, ROLE_ROUTE).hasAuthority(DELETE_ROLE)
                                .requestMatchers(HttpMethod.PUT, ROLE_ROUTE).hasAuthority(UPDATE_ROLE)
                                .requestMatchers(HttpMethod.POST, PERMISSION_ROUTE).hasAuthority(CREATE_PERMISSION)
                                .requestMatchers(HttpMethod.GET, PERMISSION_ROUTE).hasAuthority(READ_PERMISSION)
                                .requestMatchers(HttpMethod.DELETE, PERMISSION_ROUTE).hasAuthority(DELETE_PERMISSION)
                                .requestMatchers(HttpMethod.PUT, PERMISSION_ROUTE).hasAuthority(UPDATE_PERMISSION)
                                .requestMatchers(HttpMethod.POST, REGISTER_USER_ROUTE).hasAuthority(CREATE_USER)
                                .requestMatchers(HttpMethod.GET, REQUEST_ROUTE).hasAuthority(READ_REQUEST)
                                .requestMatchers(HttpMethod.POST, REQUEST_ROUTE).hasAuthority(CREATE_REQUEST)
                                .requestMatchers(HttpMethod.POST, STATUS_ROUTE).hasAuthority(CREATE_STATUS)
                                .requestMatchers(HttpMethod.DELETE, STATUS_ROUTE).hasAuthority(DELETE_STATUS)
                                .requestMatchers(HttpMethod.PUT, STATUS_ROUTE).hasAuthority(UPDATE_STATUS)

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
