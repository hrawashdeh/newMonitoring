package com.tiqmo.monitoring.auth.service;

import com.tiqmo.monitoring.auth.domain.config.ApiEndpoint;
import com.tiqmo.monitoring.auth.domain.config.ApiEndpointRepository;
import com.tiqmo.monitoring.auth.domain.config.ApiRolePermission;
import com.tiqmo.monitoring.auth.domain.config.ApiRolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiPermissionService {

    private final ApiRolePermissionRepository permissionRepository;
    private final ApiEndpointRepository endpointRepository;

    public List<ApiEndpoint> getAllEndpoints() {
        return endpointRepository.findAll();
    }

    public List<ApiRolePermission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    public List<String> getEndpointsForRole(String roleName) {
        return permissionRepository.findEndpointKeysByRoleName(roleName);
    }

    public List<String> getRolesForEndpoint(String endpointKey) {
        return permissionRepository.findRoleNamesByEndpointKey(endpointKey);
    }

    public Map<String, List<String>> getPermissionsByRole() {
        List<ApiRolePermission> allPermissions = permissionRepository.findAll();
        return allPermissions.stream()
                .collect(Collectors.groupingBy(
                        ApiRolePermission::getRoleName,
                        Collectors.mapping(ApiRolePermission::getEndpointKey, Collectors.toList())
                ));
    }

    public Map<String, List<String>> getPermissionsByEndpoint() {
        List<ApiRolePermission> allPermissions = permissionRepository.findAll();
        return allPermissions.stream()
                .collect(Collectors.groupingBy(
                        ApiRolePermission::getEndpointKey,
                        Collectors.mapping(ApiRolePermission::getRoleName, Collectors.toList())
                ));
    }

    @Transactional
    public void setPermissionsForRole(String roleName, List<String> endpointKeys, String grantedBy) {
        // Get current permissions for this role
        Set<String> currentEndpoints = new HashSet<>(permissionRepository.findEndpointKeysByRoleName(roleName));
        Set<String> newEndpoints = new HashSet<>(endpointKeys);

        // Find endpoints to add and remove
        Set<String> toAdd = new HashSet<>(newEndpoints);
        toAdd.removeAll(currentEndpoints);

        Set<String> toRemove = new HashSet<>(currentEndpoints);
        toRemove.removeAll(newEndpoints);

        // Remove old permissions
        for (String endpointKey : toRemove) {
            permissionRepository.findByEndpointKeyAndRoleName(endpointKey, roleName)
                    .ifPresent(permissionRepository::delete);
        }

        // Add new permissions
        for (String endpointKey : toAdd) {
            ApiRolePermission permission = ApiRolePermission.builder()
                    .endpointKey(endpointKey)
                    .roleName(roleName)
                    .grantedBy(grantedBy)
                    .build();
            permissionRepository.save(permission);
        }

        log.info("Updated permissions for role {}: added {}, removed {}", roleName, toAdd.size(), toRemove.size());
    }

    @Transactional
    public void grantPermission(String endpointKey, String roleName, String grantedBy) {
        if (permissionRepository.existsByEndpointKeyAndRoleName(endpointKey, roleName)) {
            log.debug("Permission already exists: {} -> {}", roleName, endpointKey);
            return;
        }

        ApiRolePermission permission = ApiRolePermission.builder()
                .endpointKey(endpointKey)
                .roleName(roleName)
                .grantedBy(grantedBy)
                .build();
        permissionRepository.save(permission);
        log.info("Granted permission: {} -> {} by {}", roleName, endpointKey, grantedBy);
    }

    @Transactional
    public void revokePermission(String endpointKey, String roleName) {
        permissionRepository.findByEndpointKeyAndRoleName(endpointKey, roleName)
                .ifPresent(permission -> {
                    permissionRepository.delete(permission);
                    log.info("Revoked permission: {} -> {}", roleName, endpointKey);
                });
    }

    @Transactional
    public void grantAllToRole(String roleName, String grantedBy) {
        List<ApiEndpoint> allEndpoints = endpointRepository.findAll();
        Set<String> currentEndpoints = new HashSet<>(permissionRepository.findEndpointKeysByRoleName(roleName));

        int added = 0;
        for (ApiEndpoint endpoint : allEndpoints) {
            if (!currentEndpoints.contains(endpoint.getEndpointKey())) {
                ApiRolePermission permission = ApiRolePermission.builder()
                        .endpointKey(endpoint.getEndpointKey())
                        .roleName(roleName)
                        .grantedBy(grantedBy)
                        .build();
                permissionRepository.save(permission);
                added++;
            }
        }
        log.info("Granted all {} endpoints to role {} by {}", added, roleName, grantedBy);
    }

    @Transactional
    public void revokeAllFromRole(String roleName) {
        permissionRepository.deleteByRoleName(roleName);
        log.info("Revoked all permissions from role {}", roleName);
    }

    public boolean hasPermission(String roleName, String endpointKey) {
        return permissionRepository.existsByEndpointKeyAndRoleName(endpointKey, roleName);
    }
}
