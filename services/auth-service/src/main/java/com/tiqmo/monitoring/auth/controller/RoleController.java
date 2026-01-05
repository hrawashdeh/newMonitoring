package com.tiqmo.monitoring.auth.controller;

import com.tiqmo.monitoring.auth.domain.Role;
import com.tiqmo.monitoring.auth.infra.config.ApiKey;
import com.tiqmo.monitoring.auth.service.RoleService;
import jakarta.validation.Valid;
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
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/roles")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:4200"})
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @ApiKey(value = "auth.roles.list", description = "List all roles")
    public ResponseEntity<List<RoleDTO>> getAllRoles() {
        List<Role> roles = roleService.getAllRoles();
        List<RoleDTO> dtos = roles.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    @ApiKey(value = "auth.roles.get", description = "Get role by ID")
    public ResponseEntity<RoleDTO> getRoleById(@PathVariable Long id) {
        return roleService.getRoleById(id)
                .map(role -> ResponseEntity.ok(toDTO(role)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ApiKey(value = "auth.roles.create", description = "Create a new role")
    public ResponseEntity<RoleDTO> createRole(@Valid @RequestBody CreateRoleRequest request) {
        Role role = roleService.createRole(request.getRoleName(), request.getDescription());
        return ResponseEntity.ok(toDTO(role));
    }

    @PutMapping("/{id}")
    @ApiKey(value = "auth.roles.update", description = "Update an existing role")
    public ResponseEntity<RoleDTO> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        Role role = roleService.updateRole(id, request.getDescription());
        return ResponseEntity.ok(toDTO(role));
    }

    @DeleteMapping("/{id}")
    @ApiKey(value = "auth.roles.delete", description = "Delete a role")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok().build();
    }

    private RoleDTO toDTO(Role role) {
        return RoleDTO.builder()
                .id(role.getId())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .createdAt(role.getCreatedAt())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleDTO {
        private Long id;
        private String roleName;
        private String description;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRoleRequest {
        @NotBlank(message = "Role name is required")
        @Size(min = 3, max = 50, message = "Role name must be between 3 and 50 characters")
        private String roleName;

        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRoleRequest {
        private String description;
    }
}
