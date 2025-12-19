package com.tiqmo.monitoring.loader.service.backfill;

import com.tiqmo.monitoring.loader.domain.loader.entity.BackfillJob;
import com.tiqmo.monitoring.loader.domain.loader.entity.BackfillJobStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.PurgeStrategy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing backfill jobs (manual data reload).
 *
 * <p>Provides operations for:
 * <ul>
 *   <li>Submitting backfill jobs</li>
 *   <li>Executing backfill jobs</li>
 *   <li>Querying job status</li>
 *   <li>Cancelling jobs</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public interface BackfillService {

    /**
     * Submits a backfill job for execution.
     *
     * <p>Creates a new backfill job in PENDING status. The job will be queued
     * for execution by the backfill processor.
     *
     * @param loaderCode    Loader code to backfill
     * @param fromTime      Start of time range to backfill
     * @param toTime        End of time range to backfill
     * @param purgeStrategy Strategy for handling existing data
     * @param requestedBy   User or system requesting backfill
     * @return Created backfill job
     * @throws IllegalArgumentException if loader not found or time range invalid
     */
    BackfillJob submitBackfillJob(String loaderCode,
                                   Instant fromTime,
                                   Instant toTime,
                                   PurgeStrategy purgeStrategy,
                                   String requestedBy);

    /**
     * Executes a backfill job.
     *
     * <p>Performs the following steps:
     * <ol>
     *   <li>Validate job is in PENDING status</li>
     *   <li>Mark job as RUNNING</li>
     *   <li>Apply purge strategy (delete existing data if needed)</li>
     *   <li>Execute loader query for time range</li>
     *   <li>Transform and ingest data</li>
     *   <li>Update job status to SUCCESS or FAILED</li>
     * </ol>
     *
     * @param jobId Backfill job ID
     * @return Updated backfill job
     * @throws IllegalArgumentException if job not found
     * @throws IllegalStateException    if job not in PENDING status
     */
    BackfillJob executeBackfillJob(Long jobId);

    /**
     * Gets a backfill job by ID.
     *
     * @param jobId Job ID
     * @return Backfill job
     */
    Optional<BackfillJob> getBackfillJob(Long jobId);

    /**
     * Gets all backfill jobs for a loader.
     *
     * @param loaderCode Loader code
     * @return List of backfill jobs (most recent first)
     */
    List<BackfillJob> getBackfillJobsByLoader(String loaderCode);

    /**
     * Gets backfill jobs by status.
     *
     * @param status Job status
     * @return List of backfill jobs (most recent first)
     */
    List<BackfillJob> getBackfillJobsByStatus(BackfillJobStatus status);

    /**
     * Gets recent backfill jobs.
     *
     * @param limit Max results
     * @return Recent backfill jobs (most recent first)
     */
    List<BackfillJob> getRecentBackfillJobs(int limit);

    /**
     * Cancels a pending backfill job.
     *
     * <p>Only PENDING jobs can be cancelled. RUNNING jobs must complete.
     *
     * @param jobId Job ID to cancel
     * @return Cancelled job
     * @throws IllegalArgumentException if job not found
     * @throws IllegalStateException    if job not in PENDING status
     */
    BackfillJob cancelBackfillJob(Long jobId);

    /**
     * Gets count of active jobs (PENDING or RUNNING).
     *
     * @return Number of active jobs
     */
    long countActiveJobs();

    /**
     * Gets count of active jobs for a specific loader.
     *
     * @param loaderCode Loader code
     * @return Number of active jobs for this loader
     */
    long countActiveJobsByLoader(String loaderCode);
}
