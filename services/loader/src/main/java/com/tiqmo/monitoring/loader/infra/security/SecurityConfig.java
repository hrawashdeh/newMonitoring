package com.tiqmo.monitoring.loader.infra.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security Configuration with JWT token validation.
 *
 * <p>This service validates JWT tokens issued by the Auth Service.
 * User authentication and token generation are handled by the Auth Service.
 *
 * <p>Security Roles (validated from JWT token):
 * <ul>
 *   <li><b>ADMIN</b>: Full access to all endpoints (CRUD, admin operations)</li>
 *   <li><b>OPERATOR</b>: Read access + operational endpoints (pause/resume, reload)</li>
 *   <li><b>VIEWER</b>: Read-only access to data endpoints</li>
 * </ul>
 *
 * <p>Public Endpoints:
 * <ul>
 *   <li>GET /actuator/health - Health check</li>
 * </ul>
 *
 * <p>Protected Endpoints:
 * <ul>
 *   <li><b>Admin Operations:</b> POST/PUT/DELETE require ROLE_ADMIN</li>
 *   <li><b>Operational Endpoints:</b> Pause/resume/reload require ROLE_OPERATOR or ROLE_ADMIN</li>
 *   <li><b>Read Endpoints:</b> GET endpoints require any authenticated user with valid JWT</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 2025-11-20
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Configure security filter chain with JWT authentication.
     *
     * @param http HttpSecurity configuration
     * @return SecurityFilterChain
     * @throws Exception if configuration error occurs
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for stateless REST API
                .csrf(AbstractHttpConfigurer::disable)

                // Configure session management (stateless with JWT)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Configure authorization rules
                // New path pattern: /api/v1/ldr/{controller-id}/**
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        // Loader CRUD - /api/v1/ldr/ldr/**
                        .requestMatchers(HttpMethod.POST, "/api/v1/ldr/ldr/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/ldr/ldr/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/ldr/ldr/**").hasRole("ADMIN")

                        // Signals - /api/v1/ldr/sig/**
                        .requestMatchers(HttpMethod.POST, "/api/v1/ldr/sig/**").hasRole("ADMIN")

                        // Admin operations - /api/v1/ldr/admn/**, /api/v1/ldr/bkfl/**, /api/v1/ldr/cfg/**
                        .requestMatchers(HttpMethod.POST, "/api/v1/ldr/admn/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/ldr/bkfl/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/ldr/cfg/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/ldr/admn/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/ldr/admn/**").hasRole("ADMIN")

                        // Approval workflow - /api/v1/ldr/apv/**
                        .requestMatchers(HttpMethod.POST, "/api/v1/ldr/apv/**").hasAnyRole("ADMIN", "OPERATOR")

                        // Read operations - all /api/v1/ldr/** GET requests
                        .requestMatchers(HttpMethod.GET, "/api/v1/ldr/**").authenticated()

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )

                // Configure exception handling for authentication failures
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            // Return 401 Unauthorized for authentication failures
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required. Please login.\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            // Return 403 Forbidden for authorization failures (authenticated but insufficient permissions)
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"You do not have permission to access this resource.\"}");
                        })
                )

                // Add JWT authentication filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
