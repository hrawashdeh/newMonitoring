package com.tiqmo.monitoring.loader.infra.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for endpoint metadata stored in Redis.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointInfo {

    /**
     * Logical key (e.g., "ldr.loaders.list")
     */
    private String key;

    /**
     * Full path (e.g., "/api/v1/ldr/ldr/loaders")
     */
    private String path;

    /**
     * HTTP method (GET, POST, PUT, DELETE)
     */
    private String httpMethod;

    /**
     * Service ID (e.g., "ldr")
     */
    private String serviceId;

    /**
     * Controller class name
     */
    private String controllerClass;

    /**
     * Method name
     */
    private String methodName;

    /**
     * Description
     */
    private String description;

    /**
     * Whether endpoint is enabled
     */
    private boolean enabled;

    /**
     * Tags for categorization
     */
    private String[] tags;

    /**
     * When this endpoint was registered
     */
    private Instant registeredAt;

    /**
     * Service instance ID that registered this
     */
    private String registeredBy;
}
