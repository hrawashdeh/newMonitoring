package com.tiqmo.monitoring.loader.domain.loader.repo;

import com.tiqmo.monitoring.loader.domain.loader.entity.LoadExecutionStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoadHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for load execution history.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Repository
public interface LoadHistoryRepository extends JpaRepository<LoadHistory, Long> {

    /**
     * Find last execution for a loader (most recent start time).
     *
     * @param loaderCode Loader code
     * @return Last execution history
     */
    Optional<LoadHistory> findFirstByLoaderCodeOrderByStartTimeDesc(String loaderCode);

    /**
     * Find last successful execution for a loader.
     *
     * @param loaderCode Loader code
     * @param status     Execution status (SUCCESS)
     * @return Last successful execution
     */
    Optional<LoadHistory> findFirstByLoaderCodeAndStatusOrderByStartTimeDesc(
        String loaderCode,
        LoadExecutionStatus status
    );

    /**
     * Find all executions for a loader within time range.
     *
     * @param loaderCode Loader code
     * @param fromTime   Start time
     * @param toTime     End time
     * @return List of executions
     */
    List<LoadHistory> findByLoaderCodeAndStartTimeBetweenOrderByStartTimeDesc(
        String loaderCode,
        Instant fromTime,
        Instant toTime
    );

    /**
     * Find executions by replica name (for debugging).
     *
     * @param replicaName Replica/pod name
     * @param limit       Max results
     * @return Recent executions by this replica
     */
    @Query("SELECT h FROM LoadHistory h WHERE h.replicaName = :replicaName " +
           "ORDER BY h.startTime DESC LIMIT :limit")
    List<LoadHistory> findRecentByReplicaName(
        @Param("replicaName") String replicaName,
        @Param("limit") int limit
    );

    /**
     * Count failed executions for a loader in time window.
     *
     * @param loaderCode Loader code
     * @param status     FAILED status
     * @param since      Time window start
     * @return Count of failures
     */
    long countByLoaderCodeAndStatusAndStartTimeAfter(
        String loaderCode,
        LoadExecutionStatus status,
        Instant since
    );

    /**
     * Delete old history records (for retention management).
     *
     * @param before Delete records before this time
     * @return Number of deleted records
     */
    @Transactional
    @Modifying
    long deleteByStartTimeBefore(Instant before);

    // ==================== Round 16: History Query Methods ====================

    /**
     * Find executions by loader code (most recent first).
     *
     * @param loaderCode Loader code
     * @param limit      Max results
     * @return Recent executions for this loader
     */
    @Query("SELECT h FROM LoadHistory h WHERE h.loaderCode = :loaderCode " +
           "ORDER BY h.startTime DESC LIMIT :limit")
    List<LoadHistory> findByLoaderCodeOrderByStartTimeDesc(
        @Param("loaderCode") String loaderCode,
        @Param("limit") int limit
    );

    /**
     * Find executions by loader code and status.
     *
     * @param loaderCode Loader code
     * @param status     Execution status
     * @param limit      Max results
     * @return Recent executions matching loader and status
     */
    @Query("SELECT h FROM LoadHistory h WHERE h.loaderCode = :loaderCode " +
           "AND h.status = :status ORDER BY h.startTime DESC LIMIT :limit")
    List<LoadHistory> findByLoaderCodeAndStatusOrderByStartTimeDesc(
        @Param("loaderCode") String loaderCode,
        @Param("status") LoadExecutionStatus status,
        @Param("limit") int limit
    );

    /**
     * Find executions by status.
     *
     * @param status Execution status
     * @param limit  Max results
     * @return Recent executions with this status
     */
    @Query("SELECT h FROM LoadHistory h WHERE h.status = :status " +
           "ORDER BY h.startTime DESC LIMIT :limit")
    List<LoadHistory> findByStatusOrderByStartTimeDesc(
        @Param("status") LoadExecutionStatus status,
        @Param("limit") int limit
    );

    /**
     * Find executions by status after a certain time.
     *
     * @param status         Execution status
     * @param startTimeAfter Lower bound for start time
     * @param limit          Max results
     * @return Executions matching criteria
     */
    @Query("SELECT h FROM LoadHistory h WHERE h.status = :status " +
           "AND h.startTime > :startTimeAfter ORDER BY h.startTime DESC LIMIT :limit")
    List<LoadHistory> findByStatusAndStartTimeAfterOrderByStartTimeDesc(
        @Param("status") LoadExecutionStatus status,
        @Param("startTimeAfter") Instant startTimeAfter,
        @Param("limit") int limit
    );

    /**
     * Find executions within time range.
     *
     * @param fromTime Start of time range
     * @param toTime   End of time range
     * @param limit    Max results
     * @return Executions within time range
     */
    @Query("SELECT h FROM LoadHistory h WHERE h.startTime BETWEEN :fromTime AND :toTime " +
           "ORDER BY h.startTime DESC LIMIT :limit")
    List<LoadHistory> findByStartTimeBetweenOrderByStartTimeDesc(
        @Param("fromTime") Instant fromTime,
        @Param("toTime") Instant toTime,
        @Param("limit") int limit
    );

    /**
     * Find most recent executions (no filters).
     *
     * @param limit Max results
     * @return Recent executions
     */
    @Query("SELECT h FROM LoadHistory h ORDER BY h.startTime DESC LIMIT :limit")
    List<LoadHistory> findRecentExecutions(@Param("limit") int limit);

    // ==================== Gap Scanner Methods ====================

    /**
     * Find executions for a loader after a certain time (for gap detection).
     *
     * @param loaderCode Loader code
     * @param startTimeAfter Lower bound for start time
     * @return Executions for this loader after the given time (ordered by start time ASC)
     */
    @Query("SELECT h FROM LoadHistory h WHERE h.loaderCode = :loaderCode " +
           "AND h.startTime > :startTimeAfter ORDER BY h.startTime ASC")
    List<LoadHistory> findByLoaderCodeAndStartTimeAfter(
        @Param("loaderCode") String loaderCode,
        @Param("startTimeAfter") Instant startTimeAfter
    );
}
