package com.tiqmo.monitoring.loader.domain.loader.entity;

import com.tiqmo.monitoring.loader.infra.security.EncryptedStringConverter;
import com.tiqmo.monitoring.workflow.domain.ChangeType;
import com.tiqmo.monitoring.workflow.domain.VersionStatus;
import com.tiqmo.monitoring.workflow.domain.WorkflowEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Archived loader versions for audit trail and rollback support.
 *
 * <p><b>Purpose:</b>
 * <ul>
 *   <li>Preserve history when ACTIVE version is replaced by newly approved version</li>
 *   <li>Store rejected drafts for audit trail</li>
 *   <li>Enable version comparison and rollback operations</li>
 * </ul>
 *
 * <p><b>Database Table:</b> loader.loader_archive
 *
 * <p><b>Key Constraints:</b>
 * <ul>
 *   <li>Unique (loader_code, version_number) - one archive record per version</li>
 *   <li>All versions preserved indefinitely (no auto-purge)</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-30
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "loader_archive", schema = "loader",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_loader_archive_version",
                           columnNames = {"loader_code", "version_number"})
       },
       indexes = {
           @Index(name = "idx_loader_archive_code", columnList = "loader_code"),
           @Index(name = "idx_loader_archive_status", columnList = "version_status"),
           @Index(name = "idx_loader_archive_rejected", columnList = "rejected_by")
       })
public class LoaderArchive implements WorkflowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID of the original loader record before archival.
     * Used to trace back to source entity.
     */
    @Column(name = "original_loader_id", nullable = false)
    private Long originalLoaderId;

    @Column(name = "loader_code", length = 64, nullable = false)
    private String loaderCode;

    /**
     * Encrypted SQL query (preserved from original loader).
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "loader_sql", columnDefinition = "TEXT", nullable = false)
    private String loaderSql;

    // ==================== SCHEDULING CONFIGURATION ====================

    @Column(name = "min_interval_seconds", nullable = false)
    private Integer minIntervalSeconds;

    @Column(name = "max_interval_seconds", nullable = false)
    private Integer maxIntervalSeconds;

    @Column(name = "max_query_period_seconds", nullable = false)
    private Integer maxQueryPeriodSeconds;

    @Column(name = "max_parallel_executions", nullable = false)
    private Integer maxParallelExecutions;

    @Column(name = "source_timezone_offset_hours")
    private Integer sourceTimezoneOffsetHours;

    // ==================== RUNTIME STATE (snapshot at archive time) ====================

    @Column(name = "last_load_timestamp")
    private Instant lastLoadTimestamp;

    @Column(name = "failed_since")
    private Instant failedSince;

    @Column(name = "consecutive_zero_record_runs")
    private Integer consecutiveZeroRecordRuns;

    @Enumerated(EnumType.STRING)
    @Column(name = "load_status", nullable = false, length = 20)
    private LoadStatus loadStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "purge_strategy", nullable = false, length = 20)
    private PurgeStrategy purgeStrategy;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "aggregation_period_seconds")
    private Integer aggregationPeriodSeconds;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // ==================== VERSIONING SYSTEM ====================

    @Enumerated(EnumType.STRING)
    @Column(name = "version_status", nullable = false, length = 20)
    private VersionStatus versionStatus;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "parent_version_id")
    private Long parentVersionId;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    @Column(name = "modified_at")
    private Instant modifiedAt;

    @Column(name = "approved_by_version", length = 100)
    private String approvedByVersion;

    @Column(name = "approved_at_version")
    private Instant approvedAtVersion;

    @Column(name = "rejected_by", length = 128)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 50)
    private ChangeType changeType;

    @Column(name = "import_label", length = 255)
    private String importLabel;

    // ==================== ARCHIVE METADATA ====================

    /**
     * Timestamp when this version was archived.
     * Set automatically by archival workflow.
     */
    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    /**
     * Username who archived this version.
     * Examples: "admin", "system", "approval-service"
     */
    @Column(name = "archived_by", nullable = false, length = 100)
    private String archivedBy;

    /**
     * Reason for archival.
     * Examples:
     * <ul>
     *   <li>"Replaced by version 3"</li>
     *   <li>"Rejected by admin"</li>
     *   <li>"Revoked due to SQL injection vulnerability"</li>
     * </ul>
     */
    @Column(name = "archive_reason", length = 500)
    private String archiveReason;

    /**
     * Source database ID (denormalized for query performance).
     * Original relationship is preserved in loader table.
     */
    @Column(name = "source_database_id", nullable = false)
    private Long sourceDatabaseId;

    // ==================== WORKFLOW ENTITY INTERFACE ====================

    @Override
    public String getEntityCode() {
        return this.loaderCode;
    }

    // ==================== FACTORY METHOD ====================

    /**
     * Create LoaderArchive from Loader entity.
     *
     * <p><b>Usage:</b>
     * <pre>
     * LoaderArchive archive = LoaderArchive.fromLoader(loader, "admin", "Replaced by version 3", Instant.now());
     * loaderArchiveRepository.save(archive);
     * </pre>
     *
     * @param loader Source loader entity
     * @param archivedBy Username archiving the loader
     * @param archiveReason Reason for archival
     * @param archivedAt Archive timestamp
     * @return New LoaderArchive entity (not yet persisted)
     */
    public static LoaderArchive fromLoader(Loader loader, String archivedBy, String archiveReason, Instant archivedAt) {
        return LoaderArchive.builder()
                // Archive metadata
                .originalLoaderId(loader.getId())
                .archivedAt(archivedAt)
                .archivedBy(archivedBy)
                .archiveReason(archiveReason)

                // Entity identity
                .loaderCode(loader.getLoaderCode())
                .loaderSql(loader.getLoaderSql())

                // Scheduling configuration
                .minIntervalSeconds(loader.getMinIntervalSeconds())
                .maxIntervalSeconds(loader.getMaxIntervalSeconds())
                .maxQueryPeriodSeconds(loader.getMaxQueryPeriodSeconds())
                .maxParallelExecutions(loader.getMaxParallelExecutions())
                .sourceTimezoneOffsetHours(loader.getSourceTimezoneOffsetHours())

                // Runtime state (snapshot)
                .lastLoadTimestamp(loader.getLastLoadTimestamp())
                .failedSince(loader.getFailedSince())
                .consecutiveZeroRecordRuns(loader.getConsecutiveZeroRecordRuns())
                .loadStatus(loader.getLoadStatus())
                .purgeStrategy(loader.getPurgeStrategy())
                .enabled(loader.isEnabled())
                .aggregationPeriodSeconds(loader.getAggregationPeriodSeconds())
                .createdAt(loader.getCreatedAt())
                .updatedAt(loader.getUpdatedAt())

                // Versioning system
                .versionStatus(loader.getVersionStatus())
                .versionNumber(loader.getVersionNumber())
                .parentVersionId(loader.getParentVersionId())
                .createdBy(loader.getCreatedBy())
                .modifiedBy(loader.getModifiedBy())
                .modifiedAt(loader.getModifiedAt())
                .approvedByVersion(loader.getApprovedByVersion())
                .approvedAtVersion(loader.getApprovedAtVersion())
                .rejectedBy(loader.getRejectedBy())
                .rejectedAt(loader.getRejectedAt())
                .rejectionReason(loader.getRejectionReason())
                .changeSummary(loader.getChangeSummary())
                .changeType(loader.getChangeType())
                .importLabel(loader.getImportLabel())

                // Source database reference (denormalized)
                .sourceDatabaseId(loader.getSourceDatabase() != null ? loader.getSourceDatabase().getId() : null)

                .build();
    }
}
