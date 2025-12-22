package com.tiqmo.monitoring.loader.domain.loader.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Execution history record for loader runs.
 *
 * <p>Tracks each load execution with:
 * <ul>
 *   <li>Timing (start/end/duration)</li>
 *   <li>Results (records loaded, errors)</li>
 *   <li>Query window (from/to timestamps)</li>
 *   <li>Replica/pod identification (for distributed execution)</li>
 * </ul>
 *
 * <p><b>Retention:</b> Consider partitioning or archiving old history records.
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
@Table(name = "load_history", schema = "loader",
       indexes = {
           @Index(name = "idx_load_history_loader_code", columnList = "loader_code"),
           @Index(name = "idx_load_history_start_time", columnList = "start_time"),
           @Index(name = "idx_load_history_status", columnList = "status"),
           @Index(name = "idx_load_history_replica", columnList = "replica_name")
       })
public class LoadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Loader code (denormalized for query performance).
     */
    @Column(name = "loader_code", nullable = false, length = 64)
    private String loaderCode;

    /**
     * Source database code (denormalized for query performance).
     */
    @Column(name = "source_database_code", nullable = false, length = 64)
    private String sourceDatabaseCode;

    // ==================== TIMING ====================

    /**
     * Execution start time.
     */
    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    /**
     * Execution end time (null if still running).
     */
    @Column(name = "end_time")
    private Instant endTime;

    /**
     * Execution duration in seconds (calculated: endTime - startTime).
     */
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    // ==================== QUERY WINDOW ====================

    /**
     * Query start time (from lastLoadTimestamp or default lookback).
     * This is the ATTEMPTED time range.
     */
    @Column(name = "query_from_time", nullable = false)
    private Instant queryFromTime;

    /**
     * Query end time (min of: fromTime + maxQueryPeriod, now()).
     * This is the ATTEMPTED time range.
     */
    @Column(name = "query_to_time", nullable = false)
    private Instant queryToTime;

    /**
     * Actual start time of loaded data (min timestamp from source query results).
     * May differ from queryFromTime if source has gaps or delays.
     */
    @Column(name = "actual_from_time")
    private Instant actualFromTime;

    /**
     * Actual end time of loaded data (max timestamp from source query results).
     * May differ from queryToTime if source has gaps or delays.
     */
    @Column(name = "actual_to_time")
    private Instant actualToTime;

    // ==================== RESULTS ====================

    /**
     * Execution status (RUNNING, SUCCESS, FAILED, PARTIAL).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LoadExecutionStatus status;

    /**
     * Number of records successfully loaded from source database.
     */
    @Column(name = "records_loaded")
    private Long recordsLoaded;

    /**
     * Number of records successfully ingested into signals_history.
     */
    @Column(name = "records_ingested")
    private Long recordsIngested;

    /**
     * Error message if execution failed (truncated to 4000 chars).
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Full stack trace if execution failed (for debugging).
     */
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    // ==================== DISTRIBUTED EXECUTION ====================

    /**
     * Replica/pod name that executed this load.
     *
     * <p>Useful for:
     * <ul>
     *   <li>Identifying which pod is executing loads</li>
     *   <li>Debugging distributed execution issues</li>
     *   <li>Load balancing analysis</li>
     * </ul>
     *
     * <p>Example: "loader-pod-1", "loader-deployment-7d8f9c-abc12"
     */
    @Column(name = "replica_name", length = 128)
    private String replicaName;

    /**
     * Version of loader configuration at time of execution.
     * Helps track what configuration was used for this run.
     */
    @Column(name = "loader_version")
    private Long loaderVersion;

    // ==================== METADATA ====================

    /**
     * Additional execution metadata (JSON).
     * Example: {"partitionKey": "shard-1", "retryCount": 2}
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
