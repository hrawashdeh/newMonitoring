package com.tiqmo.monitoring.loader.domain.config.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration Plan entity.
 * Represents a named set of configurations for a specific parent (e.g., "scheduler", "loader", "api").
 * Only one plan per parent can be active at a time.
 *
 * <p>Example usage:
 * <pre>
 * Parent: "scheduler"
 * Plans: "normal" (active), "high-load", "maintenance"
 * </pre>
 */
@Entity
@Table(name = "config_plan", schema = "loader")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Parent group for this configuration plan.
     * Examples: "scheduler", "loader", "api", "logging"
     */
    @Column(name = "parent", nullable = false, length = 64)
    private String parent;

    /**
     * Name of this configuration plan.
     * Examples: "normal", "high-load", "maintenance"
     */
    @Column(name = "plan_name", nullable = false, length = 64)
    private String planName;

    /**
     * Whether this plan is currently active.
     * Only one plan per parent can be active (enforced by unique index).
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    /**
     * Human-readable description of this plan.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Configuration values associated with this plan.
     */
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ConfigValue> configValues = new ArrayList<>();

    /**
     * Timestamp when this plan was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Timestamp when this plan was last updated.
     */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * User or system that last updated this plan.
     */
    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    /**
     * Update the updatedAt timestamp before persisting.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Helper method to add a configuration value to this plan.
     *
     * @param configValue the configuration value to add
     */
    public void addConfigValue(ConfigValue configValue) {
        configValues.add(configValue);
        configValue.setPlan(this);
    }

    /**
     * Helper method to remove a configuration value from this plan.
     *
     * @param configValue the configuration value to remove
     */
    public void removeConfigValue(ConfigValue configValue) {
        configValues.remove(configValue);
        configValue.setPlan(null);
    }

    @Override
    public String toString() {
        return "ConfigPlan{" +
                "id=" + id +
                ", parent='" + parent + '\'' +
                ", planName='" + planName + '\'' +
                ", isActive=" + isActive +
                ", configValuesCount=" + (configValues != null ? configValues.size() : 0) +
                '}';
    }
}
