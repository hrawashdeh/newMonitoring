package com.tiqmo.monitoring.loader.domain.config.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

/**
 * JPA entity for config.api_endpoints table.
 * Persists discovered API endpoints for management UI.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "api_endpoints", schema = "config")
public class ApiEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Logical API key (e.g., "ldr.loaders.list").
     */
    @Column(name = "endpoint_key", nullable = false, unique = true, length = 100)
    private String endpointKey;

    /**
     * Full path (e.g., "/api/v1/ldr/loaders").
     */
    @Column(name = "path", nullable = false, length = 255)
    private String path;

    /**
     * HTTP method (GET, POST, PUT, DELETE, PATCH).
     */
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    /**
     * Service ID (e.g., "ldr", "auth", "ie").
     */
    @Column(name = "service_id", nullable = false, length = 50)
    private String serviceId;

    /**
     * Controller class name.
     */
    @Column(name = "controller_class", length = 100)
    private String controllerClass;

    /**
     * Method name.
     */
    @Column(name = "method_name", length = 100)
    private String methodName;

    /**
     * Human-readable description.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Whether endpoint is enabled.
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Tags as JSON array.
     */
    @Column(name = "tags", columnDefinition = "JSONB")
    private String tags;

    /**
     * Status: ACTIVE, DISABLED, DEPRECATED, REMOVED.
     */
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * Last time this endpoint was seen during registration.
     */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /**
     * Service instance that last registered this.
     */
    @Column(name = "last_registered_by", length = 100)
    private String lastRegisteredBy;

    /**
     * When this record was created.
     */
    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * When this record was last updated.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSeenAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
