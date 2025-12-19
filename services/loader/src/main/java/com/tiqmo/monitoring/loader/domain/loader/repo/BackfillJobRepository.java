package com.tiqmo.monitoring.loader.domain.loader.repo;

import com.tiqmo.monitoring.loader.domain.loader.entity.BackfillJob;
import com.tiqmo.monitoring.loader.domain.loader.entity.BackfillJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for backfill jobs.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Repository
public interface BackfillJobRepository extends JpaRepository<BackfillJob, Long> {

    /**
     * Find backfill job by ID.
     *
     * @param id Job ID
     * @return Backfill job
     */
    Optional<BackfillJob> findById(Long id);

    /**
     * Find all backfill jobs for a loader (most recent first).
     *
     * @param loaderCode Loader code
     * @return List of backfill jobs
     */
    List<BackfillJob> findByLoaderCodeOrderByRequestedAtDesc(String loaderCode);

    /**
     * Find backfill jobs by status (most recent first).
     *
     * @param status Job status
     * @return List of backfill jobs
     */
    List<BackfillJob> findByStatusOrderByRequestedAtDesc(BackfillJobStatus status);

    /**
     * Find backfill jobs by loader code and status (most recent first).
     *
     * @param loaderCode Loader code
     * @param status     Job status
     * @return List of backfill jobs
     */
    List<BackfillJob> findByLoaderCodeAndStatusOrderByRequestedAtDesc(
        String loaderCode,
        BackfillJobStatus status
    );

    /**
     * Find recent backfill jobs (most recent first).
     *
     * @param limit Max results
     * @return Recent backfill jobs
     */
    @Query("SELECT b FROM BackfillJob b ORDER BY b.requestedAt DESC LIMIT :limit")
    List<BackfillJob> findRecentJobs(@Param("limit") int limit);

    /**
     * Find pending or running backfill jobs for a loader.
     *
     * @param loaderCode Loader code
     * @return List of active jobs
     */
    @Query("SELECT b FROM BackfillJob b WHERE b.loaderCode = :loaderCode " +
           "AND b.status IN ('PENDING', 'RUNNING') ORDER BY b.requestedAt ASC")
    List<BackfillJob> findActiveJobsByLoader(@Param("loaderCode") String loaderCode);

    /**
     * Find next pending job to execute.
     *
     * @return Next pending job (oldest first)
     */
    @Query("SELECT b FROM BackfillJob b WHERE b.status = 'PENDING' " +
           "ORDER BY b.requestedAt ASC LIMIT 1")
    Optional<BackfillJob> findNextPendingJob();

    /**
     * Count active jobs (PENDING or RUNNING).
     *
     * @return Number of active jobs
     */
    @Query("SELECT COUNT(b) FROM BackfillJob b WHERE b.status IN ('PENDING', 'RUNNING')")
    long countActiveJobs();

    /**
     * Count active jobs for a specific loader.
     *
     * @param loaderCode Loader code
     * @return Number of active jobs for this loader
     */
    @Query("SELECT COUNT(b) FROM BackfillJob b WHERE b.loaderCode = :loaderCode " +
           "AND b.status IN ('PENDING', 'RUNNING')")
    long countActiveJobsByLoader(@Param("loaderCode") String loaderCode);
}
