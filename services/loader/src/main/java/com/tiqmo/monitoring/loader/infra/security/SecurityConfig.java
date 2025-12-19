package com.tiqmo.monitoring.loader.infra.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security Configuration with JWT authentication and role-based access control.
 *
 * <p>Security Roles:
 * <ul>
 *   <li><b>ADMIN</b>: Full access to all endpoints (CRUD, admin operations)</li>
 *   <li><b>OPERATOR</b>: Read access + operational endpoints (pause/resume, reload)</li>
 *   <li><b>VIEWER</b>: Read-only access to data endpoints</li>
 * </ul>
 *
 * <p>Public Endpoints:
 * <ul>
 *   <li>POST /api/v1/auth/login - Authentication endpoint</li>
 *   <li>GET /actuator/health - Health check</li>
 * </ul>
 *
 * <p>Protected Endpoints:
 * <ul>
 *   <li><b>Admin Operations:</b> POST/PUT/DELETE require ROLE_ADMIN</li>
 *   <li><b>Operational Endpoints:</b> Pause/resume/reload require ROLE_OPERATOR or ROLE_ADMIN</li>
 *   <li><b>Read Endpoints:</b> GET endpoints require any authenticated user</li>
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
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        // Admin operations - require ROLE_ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/v1/res/loaders").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/res/loaders/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/res/loaders/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/res/signals/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/ops/v1/admin/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.PUT, "/ops/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/ops/v1/admin/**").hasRole("ADMIN")

                        // Read operations - require any authenticated user
                        .requestMatchers(HttpMethod.GET, "/api/v1/res/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/ops/v1/admin/**").authenticated()

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )

                // Add JWT authentication filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * In-memory user details service for development and testing.
     * In production, replace with database-backed UserDetailsService.
     *
     * <p>Default Users:
     * <ul>
     *   <li>admin / admin123 - ROLE_ADMIN (full access)</li>
     *   <li>operator / operator123 - ROLE_OPERATOR (read + operational endpoints)</li>
     *   <li>viewer / viewer123 - ROLE_VIEWER (read-only)</li>
     * </ul>
     *
     * @return UserDetailsService
     */
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder().encode("admin123"))
                .roles("ADMIN")
                .build();

        UserDetails operator = User.builder()
                .username("operator")
                .password(passwordEncoder().encode("operator123"))
                .roles("OPERATOR")
                .build();

        UserDetails viewer = User.builder()
                .username("viewer")
                .password(passwordEncoder().encode("viewer123"))
                .roles("VIEWER")
                .build();

        return new InMemoryUserDetailsManager(admin, operator, viewer);
    }

    /**
     * Password encoder for hashing passwords.
     *
     * @return BCryptPasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication manager for processing authentication requests.
     *
     * @param config authentication configuration
     * @return AuthenticationManager
     * @throws Exception if configuration error occurs
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
