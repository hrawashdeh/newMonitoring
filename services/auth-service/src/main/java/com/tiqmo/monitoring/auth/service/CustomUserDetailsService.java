package com.tiqmo.monitoring.auth.service;

import com.tiqmo.monitoring.auth.domain.User;
import com.tiqmo.monitoring.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Custom UserDetailsService implementation that loads user from database.
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String correlationId = MDC.get("correlationId");

        log.trace("Entering loadUserByUsername() | username={} | correlationId={}", username, correlationId);

        log.debug("Loading user details from database | username={} | correlationId={}", username, correlationId);

        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        log.error("USER_NOT_FOUND: User does not exist in database | username={} | correlationId={} | " +
                                        "reason=No user record found with this username | " +
                                        "suggestion=Verify username is correct or create user account",
                                username, correlationId);
                        return new UsernameNotFoundException("User not found: " + username);
                    });

            log.debug("User found in database | username={} | enabled={} | accountNonLocked={} | " +
                            "accountNonExpired={} | credentialsNonExpired={} | rolesCount={} | correlationId={}",
                    user.getUsername(), user.getEnabled(), user.getAccountNonLocked(),
                    user.getAccountNonExpired(), user.getCredentialsNonExpired(),
                    user.getRoles().size(), correlationId);

            // Log account status issues
            if (!user.getEnabled()) {
                log.warn("USER_ACCOUNT_DISABLED: User account is disabled | username={} | correlationId={} | " +
                                "reason=Account has been disabled by administrator | " +
                                "suggestion=Contact administrator to enable account",
                        username, correlationId);
            }

            if (!user.getAccountNonLocked()) {
                log.warn("USER_ACCOUNT_LOCKED: User account is locked | username={} | correlationId={} | " +
                                "reason=Account has been locked due to security policy | " +
                                "suggestion=Contact administrator to unlock account",
                        username, correlationId);
            }

            if (!user.getAccountNonExpired()) {
                log.warn("USER_ACCOUNT_EXPIRED: User account has expired | username={} | correlationId={} | " +
                                "reason=Account validity period has ended | " +
                                "suggestion=Contact administrator to renew account",
                        username, correlationId);
            }

            if (!user.getCredentialsNonExpired()) {
                log.warn("USER_CREDENTIALS_EXPIRED: User credentials have expired | username={} | correlationId={} | " +
                                "reason=Password validity period has ended | " +
                                "suggestion=Reset password to renew credentials",
                        username, correlationId);
            }

            Collection<? extends GrantedAuthority> authorities = mapRolesToAuthorities(user);

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username(user.getUsername())
                    .password(user.getPassword())
                    .authorities(authorities)
                    .accountExpired(!user.getAccountNonExpired())
                    .accountLocked(!user.getAccountNonLocked())
                    .credentialsExpired(!user.getCredentialsNonExpired())
                    .disabled(!user.getEnabled())
                    .build();

            log.info("User details loaded successfully | username={} | authorities={} | enabled={} | " +
                            "accountNonLocked={} | correlationId={}",
                    username, authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()),
                    user.getEnabled(), user.getAccountNonLocked(), correlationId);

            log.trace("Exiting loadUserByUsername() | username={} | authoritiesCount={} | success=true",
                    username, authorities.size());

            return userDetails;

        } catch (UsernameNotFoundException e) {
            log.trace("Exiting loadUserByUsername() | username={} | success=false | reason=user_not_found", username);
            throw e;

        } catch (Exception e) {
            log.error("USER_LOAD_FAILED: Unexpected error loading user details | username={} | correlationId={} | " +
                            "errorType={} | errorMessage={} | reason=Unexpected error during user details retrieval",
                    username, correlationId, e.getClass().getSimpleName(), e.getMessage(), e);
            log.trace("Exiting loadUserByUsername() | username={} | success=false | reason=unexpected_error", username);
            throw e;
        }
    }

    private Collection<? extends GrantedAuthority> mapRolesToAuthorities(User user) {
        String correlationId = MDC.get("correlationId");

        log.trace("Entering mapRolesToAuthorities() | username={} | rolesCount={} | correlationId={}",
                user.getUsername(), user.getRoles().size(), correlationId);

        Collection<? extends GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getRoleName()))
                .collect(Collectors.toList());

        log.debug("Mapped user roles to authorities | username={} | authorities={} | correlationId={}",
                user.getUsername(),
                authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()),
                correlationId);

        log.trace("Exiting mapRolesToAuthorities() | username={} | authoritiesCount={} | success=true",
                user.getUsername(), authorities.size());

        return authorities;
    }
}