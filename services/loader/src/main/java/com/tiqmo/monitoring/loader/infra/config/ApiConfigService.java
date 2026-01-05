package com.tiqmo.monitoring.loader.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for looking up endpoint paths from Redis.
 *
 * Consumers use logical keys (e.g., "ldr.loaders.list") to get
 * the current path for an endpoint.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiConfigService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${api.gateway.base-url:http://gateway:8080}")
    private String gatewayBaseUrl;

    private static final String REDIS_KEY_PREFIX = "api:endpoint:";
    private static final String REDIS_SERVICE_KEY = "api:endpoints:";

    // Local cache for frequently accessed endpoints
    private final Map<String, EndpointInfo> cache = new ConcurrentHashMap<>();

    /**
     * Get the path for an endpoint by its logical key.
     *
     * @param key Logical key (e.g., "ldr.loaders.list")
     * @return Path (e.g., "/api/v1/ldr/ldr/loaders") or null if not found
     */
    public String getPath(String key) {
        EndpointInfo info = getEndpoint(key);
        return info != null ? info.getPath() : null;
    }

    /**
     * Get full endpoint info by logical key.
     */
    public EndpointInfo getEndpoint(String key) {
        // Check local cache first
        EndpointInfo cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        // Fetch from Redis
        try {
            String redisKey = REDIS_KEY_PREFIX + key;
            String json = redisTemplate.opsForValue().get(redisKey);

            if (json != null) {
                EndpointInfo info = objectMapper.readValue(json, EndpointInfo.class);
                cache.put(key, info);
                return info;
            }

        } catch (Exception e) {
            log.error("Failed to fetch endpoint from Redis | key={} | error={}",
                    key, e.getMessage());
        }

        return null;
    }

    /**
     * Get full URL for an endpoint (gateway base + path).
     */
    public String getFullUrl(String key) {
        String path = getPath(key);
        if (path != null) {
            return gatewayBaseUrl + path;
        }
        return null;
    }

    /**
     * Check if an endpoint is enabled.
     */
    public boolean isEnabled(String key) {
        EndpointInfo info = getEndpoint(key);
        return info != null && info.isEnabled();
    }

    /**
     * Get all endpoints for a service.
     */
    public List<EndpointInfo> getEndpointsByService(String serviceId) {
        List<EndpointInfo> endpoints = new ArrayList<>();

        try {
            String serviceKey = REDIS_SERVICE_KEY + serviceId;
            String json = redisTemplate.opsForValue().get(serviceKey);

            if (json != null) {
                List<String> keys = objectMapper.readValue(json,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

                for (String key : keys) {
                    EndpointInfo info = getEndpoint(key);
                    if (info != null) {
                        endpoints.add(info);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to fetch service endpoints | serviceId={} | error={}",
                    serviceId, e.getMessage());
        }

        return endpoints;
    }

    /**
     * Get all registered services.
     */
    public Set<String> getAllServices() {
        try {
            return redisTemplate.opsForSet().members(REDIS_SERVICE_KEY + "all");
        } catch (Exception e) {
            log.error("Failed to fetch services | error={}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Get all endpoints from all services.
     */
    public List<EndpointInfo> getAllEndpoints() {
        List<EndpointInfo> allEndpoints = new ArrayList<>();

        Set<String> services = getAllServices();
        for (String serviceId : services) {
            allEndpoints.addAll(getEndpointsByService(serviceId));
        }

        return allEndpoints;
    }

    /**
     * Clear local cache (useful after config updates).
     */
    public void refreshCache() {
        cache.clear();
        log.info("Endpoint cache cleared");
    }
}
