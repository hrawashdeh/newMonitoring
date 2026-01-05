package com.tiqmo.monitoring.importexport.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiqmo.monitoring.importexport.domain.config.ApiEndpoint;
import com.tiqmo.monitoring.importexport.domain.config.ApiEndpointRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Discovers and registers API endpoints to Redis and Database at startup.
 *
 * Scans all controllers for @ApiKey annotations and registers
 * endpoint metadata to Redis (for runtime) and Database (for persistence).
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndpointRegistry {

    private final ApplicationContext applicationContext;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApiEndpointRepository apiEndpointRepository;

    @Value("${spring.application.name:import-export-service}")
    private String serviceName;

    @Value("${api.config.service-id:ie}")
    private String serviceId;

    private static final String REDIS_KEY_PREFIX = "api:endpoint:";
    private static final String REDIS_SERVICE_KEY = "api:endpoints:";
    private static final long TTL_HOURS = 24;

    @PostConstruct
    @Transactional
    public void discoverAndRegister() {
        log.info("Starting endpoint discovery for service: {}", serviceName);

        try {
            List<EndpointInfo> endpoints = discoverEndpoints();
            registerToRedis(endpoints);
            persistToDatabase(endpoints);

            log.info("Endpoint registration complete | service={} | endpointsRegistered={} | persistedToDb=true",
                    serviceName, endpoints.size());

        } catch (Exception e) {
            log.error("Failed to register endpoints | service={} | error={}",
                    serviceName, e.getMessage(), e);
        }
    }

    private List<EndpointInfo> discoverEndpoints() {
        List<EndpointInfo> endpoints = new ArrayList<>();

        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(RestController.class);

        for (Map.Entry<String, Object> entry : controllers.entrySet()) {
            Class<?> controllerClass = AopUtils.getTargetClass(entry.getValue());
            String basePath = getBasePath(controllerClass);

            log.debug("Scanning controller: {} | basePath={}", controllerClass.getSimpleName(), basePath);

            for (Method method : controllerClass.getDeclaredMethods()) {
                ApiKey apiKey = method.getAnnotation(ApiKey.class);
                if (apiKey != null) {
                    String path = basePath + getMethodPath(method);
                    String httpMethod = getHttpMethod(method);

                    EndpointInfo info = EndpointInfo.builder()
                            .key(apiKey.value())
                            .path(path)
                            .httpMethod(httpMethod)
                            .serviceId(serviceId)
                            .controllerClass(controllerClass.getSimpleName())
                            .methodName(method.getName())
                            .description(apiKey.description())
                            .enabled(apiKey.enabledByDefault())
                            .tags(apiKey.tags())
                            .registeredAt(Instant.now())
                            .registeredBy(serviceName)
                            .build();

                    endpoints.add(info);

                    log.debug("Discovered endpoint | key={} | path={} | method={}",
                            apiKey.value(), path, httpMethod);
                }
            }
        }

        return endpoints;
    }

    private void registerToRedis(List<EndpointInfo> endpoints) {
        List<String> keys = new ArrayList<>();

        for (EndpointInfo endpoint : endpoints) {
            try {
                String redisKey = REDIS_KEY_PREFIX + endpoint.getKey();
                String json = objectMapper.writeValueAsString(endpoint);

                redisTemplate.opsForValue().set(redisKey, json, TTL_HOURS, TimeUnit.HOURS);
                keys.add(endpoint.getKey());

                log.trace("Registered to Redis | key={} | path={}", endpoint.getKey(), endpoint.getPath());

            } catch (Exception e) {
                log.error("Failed to register endpoint | key={} | error={}",
                        endpoint.getKey(), e.getMessage());
            }
        }

        try {
            String serviceKey = REDIS_SERVICE_KEY + serviceId;
            redisTemplate.opsForValue().set(serviceKey,
                    objectMapper.writeValueAsString(keys), TTL_HOURS, TimeUnit.HOURS);

            redisTemplate.opsForSet().add(REDIS_SERVICE_KEY + "all", serviceId);

            log.debug("Registered service endpoint list | serviceKey={} | count={}",
                    serviceKey, keys.size());

        } catch (Exception e) {
            log.error("Failed to register service list | error={}", e.getMessage());
        }
    }

    private void persistToDatabase(List<EndpointInfo> endpoints) {
        Instant now = Instant.now();
        int inserted = 0;
        int updated = 0;

        for (EndpointInfo endpoint : endpoints) {
            try {
                ApiEndpoint existing = apiEndpointRepository.findByEndpointKey(endpoint.getKey()).orElse(null);

                if (existing != null) {
                    existing.setPath(endpoint.getPath());
                    existing.setHttpMethod(endpoint.getHttpMethod());
                    existing.setControllerClass(endpoint.getControllerClass());
                    existing.setMethodName(endpoint.getMethodName());
                    existing.setDescription(endpoint.getDescription());
                    existing.setTags(convertTagsToJson(endpoint.getTags()));
                    existing.setLastSeenAt(now);
                    existing.setLastRegisteredBy(serviceName);
                    existing.setStatus("ACTIVE");
                    apiEndpointRepository.save(existing);
                    updated++;
                } else {
                    ApiEndpoint newEndpoint = ApiEndpoint.builder()
                            .endpointKey(endpoint.getKey())
                            .path(endpoint.getPath())
                            .httpMethod(endpoint.getHttpMethod())
                            .serviceId(endpoint.getServiceId())
                            .controllerClass(endpoint.getControllerClass())
                            .methodName(endpoint.getMethodName())
                            .description(endpoint.getDescription())
                            .enabled(endpoint.isEnabled())
                            .tags(convertTagsToJson(endpoint.getTags()))
                            .status("ACTIVE")
                            .lastSeenAt(now)
                            .lastRegisteredBy(serviceName)
                            .build();
                    apiEndpointRepository.save(newEndpoint);
                    inserted++;
                }

                log.trace("Persisted endpoint to DB | key={} | action={}",
                        endpoint.getKey(), existing != null ? "update" : "insert");

            } catch (Exception e) {
                log.error("Failed to persist endpoint to DB | key={} | error={}",
                        endpoint.getKey(), e.getMessage());
            }
        }

        try {
            Instant threshold = now.minusSeconds(60);
            int removed = apiEndpointRepository.markRemovedEndpoints(serviceId, threshold);
            if (removed > 0) {
                log.info("Marked stale endpoints as REMOVED | serviceId={} | count={}", serviceId, removed);
            }
        } catch (Exception e) {
            log.warn("Failed to mark stale endpoints | error={}", e.getMessage());
        }

        log.debug("Database persistence complete | inserted={} | updated={}", inserted, updated);
    }

    private String convertTagsToJson(String[] tags) {
        if (tags == null || tags.length == 0) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(Arrays.asList(tags));
        } catch (Exception e) {
            return "[]";
        }
    }

    private String getBasePath(Class<?> controllerClass) {
        RequestMapping mapping = controllerClass.getAnnotation(RequestMapping.class);
        if (mapping != null && mapping.value().length > 0) {
            return mapping.value()[0];
        }
        return "";
    }

    private String getMethodPath(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null && get.value().length > 0) return get.value()[0];

        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null && post.value().length > 0) return post.value()[0];

        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null && put.value().length > 0) return put.value()[0];

        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null && delete.value().length > 0) return delete.value()[0];

        PatchMapping patch = method.getAnnotation(PatchMapping.class);
        if (patch != null && patch.value().length > 0) return patch.value()[0];

        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        if (mapping != null && mapping.value().length > 0) return mapping.value()[0];

        return "";
    }

    private String getHttpMethod(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) return "GET";
        if (method.isAnnotationPresent(PostMapping.class)) return "POST";
        if (method.isAnnotationPresent(PutMapping.class)) return "PUT";
        if (method.isAnnotationPresent(DeleteMapping.class)) return "DELETE";
        if (method.isAnnotationPresent(PatchMapping.class)) return "PATCH";

        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        if (mapping != null && mapping.method().length > 0) {
            return mapping.method()[0].name();
        }

        return "GET";
    }
}
