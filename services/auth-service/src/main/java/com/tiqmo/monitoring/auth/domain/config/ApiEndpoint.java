package com.tiqmo.monitoring.auth.domain.config;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

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

    @Column(name = "endpoint_key", nullable = false, unique = true, length = 100)
    private String endpointKey;

    @Column(name = "path", nullable = false, length = 255)
    private String path;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "service_id", nullable = false, length = 50)
    private String serviceId;

    @Column(name = "controller_class", length = 100)
    private String controllerClass;

    @Column(name = "method_name", length = 100)
    private String methodName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "tags", columnDefinition = "JSONB")
    private String tags;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_registered_by", length = 100)
    private String lastRegisteredBy;

    @Column(name = "created_at")
    private Instant createdAt;

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
