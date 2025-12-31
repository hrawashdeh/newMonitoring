package com.tiqmo.monitoring.loader.domain.loader.entity;

import com.tiqmo.monitoring.loader.infra.security.EncryptedStringConverter;
import com.tiqmo.monitoring.workflow.domain.ChangeType;
import com.tiqmo.monitoring.workflow.domain.VersionStatus;
import com.tiqmo.monitoring.workflow.domain.WorkflowEntity;
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
public class Loader implements WorkflowEntity {

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

    /**
     * Timestamp when this loader was created.
     */
    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * Timestamp when this loader was last updated.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ==================== VERSIONING SYSTEM ====================

    /**
     * Version status for draft/active/archive workflow.
     *
     * <p><b>State Transitions:</b>
     * <ul>
     *   <li>DRAFT → PENDING_APPROVAL (user submits for approval)</li>
     *   <li>PENDING_APPROVAL → ACTIVE (admin approves)</li>
     *   <li>PENDING_APPROVAL → ARCHIVED (admin rejects, moved to loader_archive)</li>
     *   <li>ACTIVE → ARCHIVED (new version approved, old active archived)</li>
     * </ul>
     *
     * <p><b>Constraints:</b>
     * <ul>
     *   <li>Only ONE ACTIVE version per loader_code</li>
     *   <li>Only ONE DRAFT/PENDING version per loader_code (cumulative drafts)</li>
     *   <li>Only ACTIVE versions can be enabled=true</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "version_status", nullable = false, length = 20)
    @Builder.Default
    private VersionStatus versionStatus = VersionStatus.ACTIVE;

    /**
     * Sequential version number for this loader_code.
     * Auto-incremented by database trigger across loader and loader_archive tables.
     *
     * <p>Examples:
     * <ul>
     *   <li>Version 1: Initial creation</li>
     *   <li>Version 2: First edit</li>
     *   <li>Version 3: Second edit</li>
     * </ul>
     */
    @Column(name = "version_number", nullable = false)
    @Builder.Default
    private Integer versionNumber = 1;

    /**
     * ID of the parent version this draft was based on.
     * Used for cumulative drafts and version comparison.
     *
     * <p>For new drafts: parent_version_id = current ACTIVE version's ID
     * <p>For new loaders: parent_version_id = NULL
     */
    @Column(name = "parent_version_id")
    private Long parentVersionId;

    /**
     * Username who created this version.
     *
     * <p>Examples:
     * <ul>
     *   <li>"admin" - created by admin user via UI</li>
     *   <li>"operator" - created by operator via UI</li>
     *   <li>"etl-initializer" - created by ETL initializer service</li>
     *   <li>"excel-import" - created via Excel import</li>
     * </ul>
     */
    @Column(name = "created_by", nullable = false, length = 100)
    @Builder.Default
    private String createdBy = "system";

    /**
     * Username who last modified this draft before submission.
     * Tracks intermediate edits before approval submission.
     */
    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    /**
     * Timestamp of last modification before submission.
     * Tracks when draft was last edited (not the creation time).
     */
    @Column(name = "modified_at")
    private Instant modifiedAt;

    /**
     * Admin who approved this version (when PENDING → ACTIVE).
     * Different from old approvedBy field which tracked initial approval.
     */
    @Column(name = "approved_by_version", length = 100)
    private String approvedByVersion;

    /**
     * Timestamp when this version was approved (when PENDING → ACTIVE).
     * Different from old approvedAt field which tracked initial approval.
     */
    @Column(name = "approved_at_version")
    private Instant approvedAtVersion;

    /**
     * Human-readable description of changes in this version.
     *
     * <p>Examples:
     * <ul>
     *   <li>"Updated SQL query to include new column 'category'"</li>
     *   <li>"Changed aggregation period from 60 to 300 seconds"</li>
     *   <li>"Imported from etl-data-v5.yaml"</li>
     * </ul>
     *
     * <p>Can be auto-generated from field diff or user-provided.
     */
    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    /**
     * Type of change that created this version.
     * Used for audit trail and understanding the source of modifications.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 50)
    private ChangeType changeType;

    /**
     * Import batch identifier for traceability.
     *
     * <p>Examples:
     * <ul>
     *   <li>"etl-data-v5" - from ETL initializer YAML file</li>
     *   <li>"excel-import-2025-12-30" - from Excel upload</li>
     *   <li>NULL - manual UI edit</li>
     * </ul>
     */
    @Column(name = "import_label", length = 255)
    private String importLabel;

    // ==================== APPROVAL WORKFLOW ====================

    /**
     * Approval workflow status.
     * All new loaders start as PENDING_APPROVAL and require admin approval.
     *
     * <p><b>Critical Security:</b> This field cannot be changed via regular update endpoint.
     * Only dedicated approve/reject endpoints can modify this value.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING_APPROVAL;

    /**
     * Username of admin who approved this loader.
     * Must be set when approval_status = APPROVED.
     */
    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    /**
     * Timestamp when this loader was approved.
     * Must be set when approval_status = APPROVED.
     */
    @Column(name = "approved_at")
    private Instant approvedAt;

    /**
     * Username of admin who rejected this loader.
     * Must be set when approval_status = REJECTED.
     */
    @Column(name = "rejected_by", length = 128)
    private String rejectedBy;

    /**
     * Timestamp when this loader was rejected.
     * Must be set when approval_status = REJECTED.
     */
    @Column(name = "rejected_at")
    private Instant rejectedAt;

    /**
     * Reason for rejection (required when rejected).
     * Helps loader creator understand what needs to be fixed.
     */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // ==================== RELATIONSHIPS ====================

    /**
     * Source database for this loader.
     * Passwords are automatically decrypted when loading this relationship.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_database_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_loader_source_database"))
    private SourceDatabase sourceDatabase;

    // ==================== WORKFLOW ENTITY INTERFACE ====================

    /**
     * Get entity business key (required by WorkflowEntity interface).
     * Returns the immutable unique identifier for this loader across all versions.
     *
     * @return loader_code
     */
    @Override
    public String getEntityCode() {
        return this.loaderCode;
    }

    // ==================== LIFECYCLE CALLBACKS ====================

    /**
     * Automatically set createdAt and updatedAt timestamps before persisting a new entity.
     * Called by JPA before INSERT operation.
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Automatically update updatedAt timestamp before updating an existing entity.
     * Called by JPA before UPDATE operation.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
