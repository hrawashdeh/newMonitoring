package com.tiqmo.monitoring.loader.api.admin;

import com.tiqmo.monitoring.loader.infra.config.ApiConfigService;
import com.tiqmo.monitoring.loader.infra.config.ApiKey;
import com.tiqmo.monitoring.loader.infra.config.EndpointInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin Controller for API Configuration.
 *
 * Provides endpoints for viewing and managing centralized API configuration.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/ldr/apiconf")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "API Config", description = "API configuration management")
@SecurityRequirement(name = "bearer-jwt")
public class ApiConfigController {

    private final ApiConfigService apiConfigService;

    /**
     * Get all registered endpoints for this service.
     */
    @GetMapping("/endpoints")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "List endpoints", description = "Get all registered endpoints for this service")
    @ApiKey(value = "ldr.apiconfig.endpoints", description = "List API endpoints by service")
    public ResponseEntity<List<EndpointInfo>> getEndpoints(
            @RequestParam(defaultValue = "ldr") String serviceId) {

        log.debug("GET /api/v1/ldr/apiconf/endpoints | serviceId={}", serviceId);

        List<EndpointInfo> endpoints = apiConfigService.getEndpointsByService(serviceId);
        return ResponseEntity.ok(endpoints);
    }

    /**
     * Get a specific endpoint by key.
     */
    @GetMapping("/endpoints/{key}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get endpoint", description = "Get endpoint info by logical key")
    @ApiKey(value = "ldr.apiconfig.endpoint", description = "Get single API endpoint by key")
    public ResponseEntity<EndpointInfo> getEndpoint(@PathVariable String key) {

        log.debug("GET /api/v1/ldr/apiconf/endpoints/{}", key);

        EndpointInfo info = apiConfigService.getEndpoint(key);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(info);
    }

    /**
     * Get just the path for an endpoint.
     */
    @GetMapping("/path/{key}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get path", description = "Get just the path for an endpoint key")
    @ApiKey(value = "ldr.apiconfig.path", description = "Get API endpoint path by key")
    public ResponseEntity<Map<String, String>> getPath(@PathVariable String key) {

        log.debug("GET /api/v1/ldr/apiconf/path/{}", key);

        String path = apiConfigService.getPath(key);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "key", key,
                "path", path,
                "fullUrl", apiConfigService.getFullUrl(key)
        ));
    }

    /**
     * Get all endpoints across all services.
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all endpoints", description = "Get all endpoints across all services")
    @ApiKey(value = "ldr.apiconfig.all", description = "List all API endpoints across services", tags = {"admin"})
    public ResponseEntity<Map<String, Object>> getAllEndpoints() {

        log.debug("GET /api/v1/ldr/apiconf/all");

        Set<String> services = apiConfigService.getAllServices();
        List<EndpointInfo> endpoints = apiConfigService.getAllEndpoints();

        return ResponseEntity.ok(Map.of(
                "services", services,
                "totalEndpoints", endpoints.size(),
                "endpoints", endpoints
        ));
    }

    /**
     * Refresh the local endpoint cache.
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Refresh cache", description = "Clear local endpoint cache")
    @ApiKey(value = "ldr.apiconfig.refresh", description = "Refresh API endpoint cache", tags = {"admin"})
    public ResponseEntity<Map<String, String>> refreshCache() {

        log.info("POST /api/v1/ldr/apiconf/refresh");

        apiConfigService.refreshCache();
        return ResponseEntity.ok(Map.of("status", "cache_cleared"));
    }
}
