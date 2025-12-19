package com.tiqmo.monitoring.loader.domain.loader.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Backfill job for manual data reload.
 *
 * <p>Allows reprocessing historical data for a specific time range with configurable purge strategy.
 *
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Reload data after source database downtime</li>
 *   <li>Reprocess data after bug fix in transformation logic</li>
 *   <li>Fill gaps in data after loader failures</li>
 *   <li>Rescan data after source data corrections</li>
 * </ul>
 *
 * <p><b>Purge Strategies:</b>
 * <ul>
 *   <li><b>PURGE_AND_RELOAD:</b> Delete existing data in range, then reload from source</li>
 *   <li><b>FAIL_ON_DUPLICATE:</b> Fail if data exists in range (prevents accidental overwrites)</li>
 *   <li><b>SKIP_DUPLICATES:</b> Skip existing records, load only new data</li>
 * </ul>
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
@Table(name = "backfill_job", schema = "loader",
       indexes = {
           @Index(name = "idx_backfill_loader_code", columnList = "loader_code"),
           @Index(name = "idx_backfill_status", columnList = "status"),
           @Index(name = "idx_backfill_requested_at", columnList = "requested_at")
       })
public class BackfillJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==================== LOADER REFERENCE ====================

    /**
     * Loader code to backfill.
     */
    @Column(name = "loader_code", nullable = false, length = 64)
    private String loaderCode;

    /**
     * Loader entity reference (for cascade operations).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loader_code", referencedColumnName = "loader_code",
                insertable = false, updatable = false)
    private Loader loader;

    // ==================== TIME RANGE ====================

    /**
     * Start of time range to backfill (Unix epoch seconds).
     */
    @Column(name = "from_time_epoch", nullable = false)
    private Long fromTimeEpoch;

    /**
     * End of time range to backfill (Unix epoch seconds).
     */
    @Column(name = "to_time_epoch", nullable = false)
    private Long toTimeEpoch;

    // ==================== PURGE STRATEGY ====================

    /**
     * Strategy for handling existing data in target range.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "purge_strategy", nullable = false, length = 32)
    @Builder.Default
    private PurgeStrategy purgeStrategy = PurgeStrategy.PURGE_AND_RELOAD;

    // ==================== JOB STATUS ====================

    /**
     * Current status of backfill job.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private BackfillJobStatus status = BackfillJobStatus.PENDING;

    // ==================== EXECUTION DETAILS ====================

    /**
     * Job execution start time.
     */
    @Column(name = "start_time")
    private Instant startTime;

    /**
     * Job execution end time.
     */
    @Column(name = "end_time")
    private Instant endTime;

    /**
     * Execution duration in seconds.
     */
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    // ==================== RESULTS ====================

    /**
     * Number of records purged from signals_history before reload.
     */
    @Column(name = "records_purged")
    private Long recordsPurged;

    /**
     * Number of records loaded from source database.
     */
    @Column(name = "records_loaded")
    private Long recordsLoaded;

    /**
     * Number of records ingested into signals_history.
     */
    @Column(name = "records_ingested")
    private Long recordsIngested;

    // ==================== ERROR TRACKING ====================

    /**
     * Error message if job failed.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Full stack trace if job failed.
     */
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    // ==================== METADATA ====================

    /**
     * User or system that requested backfill.
     */
    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    /**
     * Time when backfill was requested.
     */
    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private Instant requestedAt = Instant.now();

    /**
     * Replica/pod that executed backfill.
     */
    @Column(name = "replica_name", length = 64)
    private String replicaName;

    // ==================== AUDIT ====================

    /**
     * Record creation timestamp.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Record last update timestamp.
     */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Updates the updatedAt timestamp before persistence.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Gets from time as Instant.
     */
    public Instant getFromTimeInstant() {
        return fromTimeEpoch != null ? Instant.ofEpochSecond(fromTimeEpoch) : null;
    }

    /**
     * Gets to time as Instant.
     */
    public Instant getToTimeInstant() {
        return toTimeEpoch != null ? Instant.ofEpochSecond(toTimeEpoch) : null;
    }

    /**
     * Sets from time from Instant.
     */
    public void setFromTimeInstant(Instant instant) {
        this.fromTimeEpoch = instant != null ? instant.getEpochSecond() : null;
    }

    /**
     * Sets to time from Instant.
     */
    public void setToTimeInstant(Instant instant) {
        this.toTimeEpoch = instant != null ? instant.getEpochSecond() : null;
    }
}
