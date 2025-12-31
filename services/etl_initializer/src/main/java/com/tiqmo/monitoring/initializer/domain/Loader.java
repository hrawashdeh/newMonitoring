package com.tiqmo.monitoring.initializer.domain;

import com.tiqmo.monitoring.initializer.domain.enums.LoadStatus;
import com.tiqmo.monitoring.initializer.domain.enums.PurgeStrategy;
import com.tiqmo.monitoring.initializer.infra.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * ETL Loader configuration for scheduled data extraction.
 *
 * <p><b>Scheduling Strategy:</b>
 * <ul>
 *   <li><b>Maximum Interval (start-to-start):</b> How often load should run (e.g., every 60 seconds)</li>
 *   <li><b>Minimum Interval (end-to-start):</b> Wait time after load completes (e.g., wait 10 seconds)</li>
 *   <li><b>Max Query Period:</b> Limit historical data per run (e.g., 5 days at a time)</li>
 * </ul>
 *
 * <p><b>Distributed Execution:</b>
 * Uses pessimistic locking (SELECT FOR UPDATE) to prevent duplicate execution across replicas/pods.
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
@Table(name = "loader", schema = "loader",
       indexes = {
           @Index(name = "idx_loader_status", columnList = "load_status"),
           @Index(name = "idx_loader_enabled", columnList = "enabled"),
           @Index(name = "idx_loader_source_db", columnList = "source_database_id")
       })
public class Loader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loader_code", length = 64, nullable = false, unique = true)
    private String loaderCode;

    /**
     * Encrypted SQL query (AES-256-GCM).
     * Automatically encrypted before save and decrypted after load.
     * Supports Arabic characters in SQL strings (UTF-8).
     *
     * <p><b>Time Filter Placeholders:</b>
     * <ul>
     *   <li>{@code :fromTime} - Query start time (replaced with lastLoadTimestamp)</li>
     *   <li>{@code :toTime} - Query end time (replaced with current time or max query period)</li>
     * </ul>
     *
     * <p>Example: {@code SELECT * FROM users WHERE created_at BETWEEN :fromTime AND :toTime}
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "loader_sql", columnDefinition = "TEXT", nullable = false)
    private String loaderSql;

    // ==================== SCHEDULING CONFIGURATION ====================

    /**
     * Minimum interval (end-to-start): Wait time after load completes.
     *
     * <p>Example: If load takes 50 seconds and minIntervalSeconds=10,
     * wait 10 seconds before next run (total cycle = 60 seconds).
     */
    @Column(name = "min_interval_seconds", nullable = false)
    private Integer minIntervalSeconds;

    /**
     * Maximum interval (start-to-start): How often load should run.
     *
     * <p>Example: maxIntervalSeconds=60 means run every 60 seconds
     * regardless of load duration.
     */
    @Column(name = "max_interval_seconds", nullable = false)
    private Integer maxIntervalSeconds;

    /**
     * Maximum query period: Limit historical data per run (in seconds).
     *
     * <p>Example: If last load was 30 days ago and maxQueryPeriodSeconds=5 days (432000),
     * load 5 days at a time until caught up.
     *
     * <p>Prevents overwhelming source database with large historical queries.
     */
    @Column(name = "max_query_period_seconds", nullable = false)
    private Integer maxQueryPeriodSeconds;

    /**
     * Maximum parallel executions allowed for this loader across all replicas.
     *
     * <p>Example: maxParallelExecutions=2 allows 2 pods to run this loader simultaneously.
     * Useful for partitioned loaders.
     */
    @Column(name = "max_parallel_executions", nullable = false)
    private Integer maxParallelExecutions;

    /**
     * Timezone offset (in hours) of the source database.
     * Used to normalize timestamps to UTC during data loading.
     *
     * <p><b>Issue #2.1:</b> Solves time mismatch between data generator and loader.
     *
     * <p>Examples:
     * <ul>
     *   <li>GMT+4 (Dubai, UAE) → sourceTimezoneOffsetHours = 4</li>
     *   <li>EST (UTC-5) → sourceTimezoneOffsetHours = -5</li>
     *   <li>UTC (default) → sourceTimezoneOffsetHours = 0 or null</li>
     * </ul>
     *
     * <p><b>Query Window Adjustment:</b>
     * When querying, subtract offset from UTC times to get source DB times.
     * Example: Query for UTC 14:00, source is GMT+4 → query for 10:00 in source DB.
     *
     * <p><b>Data Normalization:</b>
     * When loading, add offset to source timestamps to get UTC.
     * Example: Source timestamp 10:00 GMT+4 → stored as 14:00 UTC.
     *
     * <p>If null or 0, no timezone conversion is performed (source assumed UTC).
     */
    @Column(name = "source_timezone_offset_hours")
    @Builder.Default
    private Integer sourceTimezoneOffsetHours = 0;

    // ==================== RUNTIME STATE ====================

    /**
     * Last successful load timestamp (query end time).
     * Next load will start from this point.
     *
     * <p><b>Adjustable via API:</b> Can be modified to reprocess historical data.
     *
     * <p>If null, loader will start from default lookback period (e.g., 1 day ago).
     */
    @Column(name = "last_load_timestamp")
    private Instant lastLoadTimestamp;

    /**
     * Timestamp when loader entered FAILED status.
     * Used for auto-recovery: resets to IDLE after 20 minutes.
     */
    @Column(name = "failed_since")
    private Instant failedSince;

    /**
     * Number of consecutive executions that loaded zero records.
     * Used to detect prolonged source downtime or data issues.
     *
     * <p>Reset to 0 when records are successfully loaded.
     * Triggers warning log when threshold exceeded (e.g., 10 consecutive runs).
     *
     * <p>Example: If source DB has 16-hour downtime, loader won't get stuck
     * but will log warnings about the prolonged zero-record period.
     */
    @Column(name = "consecutive_zero_record_runs")
    @Builder.Default
    private Integer consecutiveZeroRecordRuns = 0;

    /**
     * Current load status.
     *
     * <p>Transitions:
     * <ul>
     *   <li>IDLE → RUNNING (when execution starts)</li>
     *   <li>RUNNING → IDLE (on success)</li>
     *   <li>RUNNING → FAILED (on error)</li>
     *   <li>FAILED → IDLE (auto-recovery after 20 minutes)</li>
     *   <li>Any → PAUSED (manual pause)</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "load_status", nullable = false, length = 20)
    @Builder.Default
    private LoadStatus loadStatus = LoadStatus.IDLE;

    /**
     * Strategy for handling data beyond last load timestamp.
     *
     * <p>When lastLoadTimestamp is adjusted backwards via API:
     * <ul>
     *   <li>FAIL_ON_DUPLICATE: Raise error if duplicate data exists</li>
     *   <li>PURGE_AND_RELOAD: Delete existing data before reloading</li>
     *   <li>SKIP_DUPLICATES: Keep existing data, skip reload</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "purge_strategy", nullable = false, length = 20)
    @Builder.Default
    private PurgeStrategy purgeStrategy = PurgeStrategy.FAIL_ON_DUPLICATE;

    /**
     * Enable/disable this loader.
     * Disabled loaders are not scheduled for execution.
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * Aggregation period in seconds for signal data.
     * Used by detection and scanning logic.
     */
    @Column(name = "aggregation_period_seconds")
    private Integer aggregationPeriodSeconds;

    // ==================== RELATIONSHIPS ====================

    /**
     * Source database for this loader.
     * Passwords are automatically decrypted when loading this relationship.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_database_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_loader_source_database"))
    private SourceDatabase sourceDatabase;
}
