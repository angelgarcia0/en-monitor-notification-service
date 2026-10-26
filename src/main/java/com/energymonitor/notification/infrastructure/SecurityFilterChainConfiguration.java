package com.energymonitor.notification.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * The HTTP security rules of this service's API.
 *
 * <p>The security service issues the access tokens; this service only validates them and reads
 * the caller from their subject. A stateless bearer-token API: no HTTP session, no CSRF (there is
 * no cookie for a cross-site request to ride on), no form login and no HTTP Basic. JWT signature,
 * issuer and expiry are checked by the resource server filter before a request reaches a
 * controller.
 *
 * <p>The administrator notifications under {@code /api/v1/admin/notifications} additionally
 * require {@code USER.READ}, asked of the security service; when it does not answer they are
 * refused with 503. Any other {@code /api/v1/admin/**} route is denied.
 *
 * <p>The clients reach this service through the API gateway, which answers CORS, so no CORS
 * policy is published here. Anything that is not the API or the health probe is denied, so a new
 * endpoint stays closed until it is placed under {@code /api/v1}.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityFilterChainConfiguration {

    /**
     * Unauthenticated access to the API description and the health probe, so a container
     * platform can still check liveness.
     */
    private static final String[] INFRASTRUCTURE_ENDPOINTS = {
        "/actuator/health",
        "/actuator/health/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthenticationEntryPoint entryPoint,
                                                    JwtAccessDeniedHandler accessDenied,
                                                    PermissionAuthorization permissions,
                                                    ObjectMapper objectMapper,
                                                    Clock clock)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .addFilterBefore(new SecurityServiceUnavailableFilter(objectMapper, clock),
                        ExceptionTranslationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(INFRASTRUCTURE_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/notifications")
                        .access(permissions.require(PermissionCodes.USER_READ))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/notifications/*/read")
                        .access(permissions.require(PermissionCodes.USER_READ))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/notifications/*")
                        .access(permissions.require(PermissionCodes.USER_READ))
                        .requestMatchers("/api/v1/admin/**").denyAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied)
                        .jwt(Customizer.withDefaults()))
                .build();
    }
}
