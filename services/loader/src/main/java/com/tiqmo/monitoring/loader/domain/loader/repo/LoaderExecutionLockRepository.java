package com.tiqmo.monitoring.loader.domain.loader.repo;

import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderExecutionLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for distributed execution locks.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Repository
public interface LoaderExecutionLockRepository extends JpaRepository<LoaderExecutionLock, Long> {

    /**
     * Count active (unreleased) locks for a loader.
     * Used to enforce maxParallelExecutions limit.
     *
     * @param loaderCode Loader code
     * @param released   false (active locks)
     * @return Number of active locks
     */
    long countByLoaderCodeAndReleased(String loaderCode, Boolean released);

    /**
     * Count all active (unreleased) locks across all loaders.
     * Used to enforce global concurrent execution limit.
     *
     * @param released false (active locks)
     * @return Total active locks
     */
    long countByReleased(Boolean released);

    /**
     * Find active locks for a loader.
     *
     * @param loaderCode Loader code
     * @param released   false (active locks)
     * @return List of active locks
     */
    List<LoaderExecutionLock> findByLoaderCodeAndReleased(String loaderCode, Boolean released);

    /**
     * Find lock by unique lock ID.
     *
     * @param lockId UUID lock identifier
     * @return Lock if found
     */
    Optional<LoaderExecutionLock> findByLockId(String lockId);

    /**
     * Release a specific lock (mark as released).
     *
     * @param lockId      UUID lock identifier
     * @param releasedAt  Release timestamp
     * @return Number of updated records (1 if successful)
     */
    @Modifying
    @Query("UPDATE LoaderExecutionLock l SET l.released = true, l.releasedAt = :releasedAt " +
           "WHERE l.lockId = :lockId AND l.released = false")
    int releaseLock(@Param("lockId") String lockId, @Param("releasedAt") Instant releasedAt);

    /**
     * Find stale locks (acquired but not released for > 2 hours).
     * These likely indicate pod crashes and should be cleaned up.
     *
     * @param staleThreshold Time before which locks are considered stale
     * @param released       false (unreleased locks)
     * @return List of stale locks
     */
    List<LoaderExecutionLock> findByReleasedAndAcquiredAtBefore(Boolean released, Instant staleThreshold);

    /**
     * Clean up stale locks (auto-release locks from crashed pods).
     *
     * @param staleThreshold Time before which locks are considered stale
     * @param releasedAt     Timestamp to set for release
     * @return Number of locks released
     */
    @Modifying
    @Query("UPDATE LoaderExecutionLock l SET l.released = true, l.releasedAt = :releasedAt " +
           "WHERE l.released = false AND l.acquiredAt < :staleThreshold")
    int cleanupStaleLocks(@Param("staleThreshold") Instant staleThreshold,
                          @Param("releasedAt") Instant releasedAt);

    /**
     * Delete old released locks (retention management).
     *
     * @param before Delete locks released before this time
     * @return Number of deleted locks
     */
    long deleteByReleasedAndReleasedAtBefore(Boolean released, Instant before);
}
