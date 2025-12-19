package com.tiqmo.monitoring.loader.domain.config.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Configuration Value entity.
 * Represents a single key-value configuration entry within a ConfigPlan.
 *
 * <p>Example:
 * <pre>
 * configKey = "scheduler.polling-interval-seconds"
 * configValue = "1"
 * dataType = INTEGER
 * </pre>
 */
@Entity
@Table(name = "config_value", schema = "loader")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The configuration plan this value belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private ConfigPlan plan;

    /**
     * Configuration key using dot notation.
     * Examples: "polling-interval-seconds", "thread-pool-core-size", "rotation.max-file-size"
     */
    @Column(name = "config_key", nullable = false, length = 128)
    private String configKey;

    /**
     * Configuration value as a string.
     * Will be parsed based on dataType by the application.
     */
    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    /**
     * Data type for this configuration value.
     * Used to parse the string value into the appropriate Java type.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 32)
    private ConfigDataType dataType;

    /**
     * Human-readable description of this configuration value.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Override
    public String toString() {
        return "ConfigValue{" +
                "id=" + id +
                ", configKey='" + configKey + '\'' +
                ", configValue='" + configValue + '\'' +
                ", dataType=" + dataType +
                '}';
    }
}