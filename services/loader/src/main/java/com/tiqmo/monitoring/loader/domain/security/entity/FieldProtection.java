package com.tiqmo.monitoring.loader.domain.security.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Field-Level Protection Configuration Entity
 *
 * Maps to resource_management.field_protection table.
 * Defines which fields are visible/hidden for which roles per resource type.
 */
@Entity
@Table(name = "field_protection", schema = "resource_management")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldProtection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "role_code", nullable = false, length = 50)
    private String roleCode;

    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible;

    @Column(name = "redaction_type", length = 20)
    private String redactionType;

    @Column(name = "redaction_value", columnDefinition = "TEXT")
    private String redactionValue;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
