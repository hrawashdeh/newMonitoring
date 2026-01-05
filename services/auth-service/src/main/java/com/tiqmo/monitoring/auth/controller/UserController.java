package com.tiqmo.monitoring.auth.controller;

import com.tiqmo.monitoring.auth.domain.Role;
import com.tiqmo.monitoring.auth.domain.User;
import com.tiqmo.monitoring.auth.infra.config.ApiKey;
import com.tiqmo.monitoring.auth.security.JwtTokenProvider;
import com.tiqmo.monitoring.auth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:4200"})
public class UserController {

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;

    @GetMapping
    @ApiKey(value = "auth.users.list", description = "List all users")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        List<UserDTO> dtos = users.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    @ApiKey(value = "auth.users.get", description = "Get user by ID")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(user -> ResponseEntity.ok(toDTO(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ApiKey(value = "auth.users.create", description = "Create a new user")
    public ResponseEntity<UserDTO> createUser(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest) {
        String currentUser = getCurrentUsername(httpRequest);
        User user = userService.createUser(
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getFullName(),
                request.getRoles(),
                currentUser
        );
        return ResponseEntity.ok(toDTO(user));
    }

    @PutMapping("/{id}")
    @ApiKey(value = "auth.users.update", description = "Update an existing user")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            HttpServletRequest httpRequest) {
        String currentUser = getCurrentUsername(httpRequest);
        User user = userService.updateUser(
                id,
                request.getEmail(),
                request.getFullName(),
                request.getEnabled(),
                request.getRoles(),
                currentUser
        );
        return ResponseEntity.ok(toDTO(user));
    }

    @PostMapping("/{id}/change-password")
    @ApiKey(value = "auth.users.changePassword", description = "Change user password")
    public ResponseEntity<Void> changePassword(
            @PathVariable Long id,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        String currentUser = getCurrentUsername(httpRequest);
        userService.changePassword(id, request.getNewPassword(), currentUser);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/toggle-enabled")
    @ApiKey(value = "auth.users.toggleEnabled", description = "Toggle user enabled status")
    public ResponseEntity<UserDTO> toggleEnabled(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String currentUser = getCurrentUsername(httpRequest);
        userService.toggleUserEnabled(id, currentUser);
        return userService.getUserById(id)
                .map(user -> ResponseEntity.ok(toDTO(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ApiKey(value = "auth.users.delete", description = "Delete a user")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    private String getCurrentUsername(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return tokenProvider.getUsernameFromToken(token);
        }
        return "system";
    }

    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .enabled(user.getEnabled())
                .accountNonExpired(user.getAccountNonExpired())
                .accountNonLocked(user.getAccountNonLocked())
                .credentialsNonExpired(user.getCredentialsNonExpired())
                .roles(user.getRoles().stream()
                        .map(Role::getRoleName)
                        .collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .createdBy(user.getCreatedBy())
                .updatedBy(user.getUpdatedBy())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDTO {
        private Long id;
        private String username;
        private String email;
        private String fullName;
        private Boolean enabled;
        private Boolean accountNonExpired;
        private Boolean accountNonLocked;
        private Boolean credentialsNonExpired;
        private Set<String> roles;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime lastLoginAt;
        private String createdBy;
        private String updatedBy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateUserRequest {
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        private String username;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        @Email(message = "Email must be valid")
        private String email;

        private String fullName;

        private Set<String> roles;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateUserRequest {
        @Email(message = "Email must be valid")
        private String email;

        private String fullName;
        private Boolean enabled;
        private Set<String> roles;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePasswordRequest {
        @NotBlank(message = "New password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String newPassword;
    }
}
