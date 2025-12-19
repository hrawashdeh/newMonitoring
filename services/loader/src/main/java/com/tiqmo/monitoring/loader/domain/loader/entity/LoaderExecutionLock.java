package com.tiqmo.monitoring.loader.domain.loader.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Distributed execution lock for loaders.
 *
 * <p>Prevents duplicate execution across multiple replicas/pods using optimistic locking.
 * Each running execution creates a lock entry that is released on completion.
 *
 * <p><b>Lock Strategy:</b>
 * <ol>
 *   <li>Before execution: INSERT lock record</li>
 *   <li>Check active locks: COUNT(*) WHERE loaderCode = ? AND released = false</li>
 *   <li>If count <= maxParallelExecutions: proceed with execution</li>
 *   <li>After execution: UPDATE released = true, releasedAt = now()</li>
 * </ol>
 *
 * <p><b>Stale Lock Cleanup:</b>
 * Locks older than 2 hours with released=false are considered stale (pod crashed).
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
@Table(name = "loader_execution_lock", schema = "loader",
       indexes = {
           @Index(name = "idx_lock_loader_released", columnList = "loader_code,released"),
           @Index(name = "idx_lock_acquired_at", columnList = "acquired_at")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_lock_id", columnNames = "lock_id")
       })
public class LoaderExecutionLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique lock identifier (UUID).
     * Used to identify specific lock for release.
     */
    @Column(name = "lock_id", nullable = false, length = 64, unique = true)
    private String lockId;

    /**
     * Loader code this lock is for.
     */
    @Column(name = "loader_code", nullable = false, length = 64)
    private String loaderCode;

    /**
     * Replica/pod name that acquired this lock.
     */
    @Column(name = "replica_name", nullable = false, length = 128)
    private String replicaName;

    /**
     * When lock was acquired (execution started).
     */
    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    /**
     * When lock was released (execution completed).
     * Null if still held.
     */
    @Column(name = "released_at")
    private Instant releasedAt;

    /**
     * Whether lock has been released.
     * Used for querying active locks efficiently.
     */
    @Column(name = "released", nullable = false)
    @Builder.Default
    private Boolean released = false;

    /**
     * Load history ID associated with this lock.
     * Links lock to execution record.
     */
    @Column(name = "load_history_id")
    private Long loadHistoryId;

    /**
     * Version for optimistic locking.
     * Prevents concurrent lock modifications.
     */
    @Version
    @Column(name = "version")
    private Long version;
}
