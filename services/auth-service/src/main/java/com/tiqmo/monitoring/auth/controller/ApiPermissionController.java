package com.tiqmo.monitoring.auth.controller;

import com.tiqmo.monitoring.auth.domain.config.ApiEndpoint;
import com.tiqmo.monitoring.auth.domain.config.ApiRolePermission;
import com.tiqmo.monitoring.auth.infra.config.ApiKey;
import com.tiqmo.monitoring.auth.security.JwtTokenProvider;
import com.tiqmo.monitoring.auth.service.ApiPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/permissions")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:4200"})
public class ApiPermissionController {

    private final ApiPermissionService permissionService;
    private final JwtTokenProvider tokenProvider;

    @GetMapping("/endpoints")
    @ApiKey(value = "auth.permissions.endpoints", description = "List all API endpoints")
    public ResponseEntity<List<EndpointDTO>> getAllEndpoints() {
        List<ApiEndpoint> endpoints = permissionService.getAllEndpoints();
        List<EndpointDTO> dtos = endpoints.stream()
                .map(this::toEndpointDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping
    @ApiKey(value = "auth.permissions.list", description = "List all permissions")
    public ResponseEntity<List<PermissionDTO>> getAllPermissions() {
        List<ApiRolePermission> permissions = permissionService.getAllPermissions();
        List<PermissionDTO> dtos = permissions.stream()
                .map(this::toPermissionDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/by-role")
    @ApiKey(value = "auth.permissions.byRole", description = "Get permissions grouped by role")
    public ResponseEntity<Map<String, List<String>>> getPermissionsByRole() {
        return ResponseEntity.ok(permissionService.getPermissionsByRole());
    }

    @GetMapping("/by-endpoint")
    @ApiKey(value = "auth.permissions.byEndpoint", description = "Get permissions grouped by endpoint")
    public ResponseEntity<Map<String, List<String>>> getPermissionsByEndpoint() {
        return ResponseEntity.ok(permissionService.getPermissionsByEndpoint());
    }

    @GetMapping("/role/{roleName}")
    @ApiKey(value = "auth.permissions.forRole", description = "Get endpoints for a specific role")
    public ResponseEntity<List<String>> getEndpointsForRole(@PathVariable String roleName) {
        return ResponseEntity.ok(permissionService.getEndpointsForRole(roleName));
    }

    @GetMapping("/endpoint/{endpointKey}")
    @ApiKey(value = "auth.permissions.forEndpoint", description = "Get roles for a specific endpoint")
    public ResponseEntity<List<String>> getRolesForEndpoint(@PathVariable String endpointKey) {
        return ResponseEntity.ok(permissionService.getRolesForEndpoint(endpointKey));
    }

    @PutMapping("/role/{roleName}")
    @ApiKey(value = "auth.permissions.setForRole", description = "Set permissions for a role")
    public ResponseEntity<Void> setPermissionsForRole(
            @PathVariable String roleName,
            @Valid @RequestBody SetPermissionsRequest request,
            HttpServletRequest httpRequest) {
        String currentUser = getCurrentUsername(httpRequest);
        permissionService.setPermissionsForRole(roleName, request.getEndpointKeys(), currentUser);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/grant")
    @ApiKey(value = "auth.permissions.grant", description = "Grant a permission to a role")
    public ResponseEntity<Void> grantPermission(
            @Valid @RequestBody PermissionRequest request,
            HttpServletRequest httpRequest) {
        String currentUser = getCurrentUsername(httpRequest);
        permissionService.grantPermission(request.getEndpointKey(), request.getRoleName(), currentUser);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/revoke")
    @ApiKey(value = "auth.permissions.revoke", description = "Revoke a permission from a role")
    public ResponseEntity<Void> revokePermission(@Valid @RequestBody PermissionRequest request) {
        permissionService.revokePermission(request.getEndpointKey(), request.getRoleName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/role/{roleName}/grant-all")
    @ApiKey(value = "auth.permissions.grantAll", description = "Grant all permissions to a role")
    public ResponseEntity<Void> grantAllToRole(
            @PathVariable String roleName,
            HttpServletRequest httpRequest) {
        String currentUser = getCurrentUsername(httpRequest);
        permissionService.grantAllToRole(roleName, currentUser);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/role/{roleName}/revoke-all")
    @ApiKey(value = "auth.permissions.revokeAll", description = "Revoke all permissions from a role")
    public ResponseEntity<Void> revokeAllFromRole(@PathVariable String roleName) {
        permissionService.revokeAllFromRole(roleName);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check")
    @ApiKey(value = "auth.permissions.check", description = "Check if a role has a permission")
    public ResponseEntity<Boolean> checkPermission(
            @RequestParam String roleName,
            @RequestParam String endpointKey) {
        return ResponseEntity.ok(permissionService.hasPermission(roleName, endpointKey));
    }

    private String getCurrentUsername(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return tokenProvider.getUsernameFromToken(token);
        }
        return "system";
    }

    private EndpointDTO toEndpointDTO(ApiEndpoint endpoint) {
        return EndpointDTO.builder()
                .id(endpoint.getId())
                .endpointKey(endpoint.getEndpointKey())
                .path(endpoint.getPath())
                .httpMethod(endpoint.getHttpMethod())
                .serviceId(endpoint.getServiceId())
                .controllerClass(endpoint.getControllerClass())
                .methodName(endpoint.getMethodName())
                .description(endpoint.getDescription())
                .enabled(endpoint.getEnabled())
                .tags(endpoint.getTags())
                .status(endpoint.getStatus())
                .lastSeenAt(endpoint.getLastSeenAt())
                .build();
    }

    private PermissionDTO toPermissionDTO(ApiRolePermission permission) {
        return PermissionDTO.builder()
                .id(permission.getId())
                .endpointKey(permission.getEndpointKey())
                .roleName(permission.getRoleName())
                .grantedBy(permission.getGrantedBy())
                .grantedAt(permission.getGrantedAt())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointDTO {
        private Integer id;
        private String endpointKey;
        private String path;
        private String httpMethod;
        private String serviceId;
        private String controllerClass;
        private String methodName;
        private String description;
        private Boolean enabled;
        private String tags;
        private String status;
        private Instant lastSeenAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionDTO {
        private Integer id;
        private String endpointKey;
        private String roleName;
        private String grantedBy;
        private Instant grantedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetPermissionsRequest {
        @NotNull(message = "Endpoint keys are required")
        private List<String> endpointKeys;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionRequest {
        @NotBlank(message = "Endpoint key is required")
        private String endpointKey;

        @NotBlank(message = "Role name is required")
        private String roleName;
    }
}
