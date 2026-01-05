package com.tiqmo.monitoring.auth.service;

import com.tiqmo.monitoring.auth.domain.Role;
import com.tiqmo.monitoring.auth.domain.User;
import com.tiqmo.monitoring.auth.repository.RoleRepository;
import com.tiqmo.monitoring.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User createUser(String username, String password, String email, String fullName,
                          Set<String> roleNames, String createdBy) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }

        Set<Role> roles = roleNames.stream()
                .map(roleName -> roleRepository.findByRoleName(roleName)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName)))
                .collect(Collectors.toSet());

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .fullName(fullName)
                .enabled(true)
                .roles(roles)
                .createdBy(createdBy)
                .build();

        User saved = userRepository.save(user);
        log.info("Created user: {} with roles: {}", username, roleNames);
        return saved;
    }

    @Transactional
    public User updateUser(Long id, String email, String fullName, Boolean enabled,
                          Set<String> roleNames, String updatedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        if (email != null) {
            // Check if email is being changed to an existing email
            if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("Email already exists: " + email);
            }
            user.setEmail(email);
        }

        if (fullName != null) {
            user.setFullName(fullName);
        }

        if (enabled != null) {
            user.setEnabled(enabled);
        }

        if (roleNames != null && !roleNames.isEmpty()) {
            Set<Role> roles = roleNames.stream()
                    .map(roleName -> roleRepository.findByRoleName(roleName)
                            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName)))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }

        user.setUpdatedBy(updatedBy);
        User saved = userRepository.save(user);
        log.info("Updated user: {} by {}", user.getUsername(), updatedBy);
        return saved;
    }

    @Transactional
    public void changePassword(Long id, String newPassword, String updatedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedBy(updatedBy);
        userRepository.save(user);
        log.info("Password changed for user: {} by {}", user.getUsername(), updatedBy);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        userRepository.delete(user);
        log.info("Deleted user: {}", user.getUsername());
    }

    @Transactional
    public void toggleUserEnabled(Long id, String updatedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        user.setEnabled(!user.getEnabled());
        user.setUpdatedBy(updatedBy);
        userRepository.save(user);
        log.info("Toggled enabled status for user: {} to {} by {}",
                user.getUsername(), user.getEnabled(), updatedBy);
    }
}
