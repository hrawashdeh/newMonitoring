package com.tiqmo.monitoring.loader.service.backfill;

import com.tiqmo.monitoring.loader.domain.loader.entity.*;
import com.tiqmo.monitoring.loader.domain.loader.repo.BackfillJobRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import com.tiqmo.monitoring.loader.domain.signals.repo.SignalsHistoryRepository;
import com.tiqmo.monitoring.loader.dto.common.ErrorCode;
import com.tiqmo.monitoring.loader.exception.BusinessException;
import com.tiqmo.monitoring.loader.infra.ReplicaNameProvider;
import com.tiqmo.monitoring.loader.infra.db.SourceDbManager;
import com.tiqmo.monitoring.loader.service.execution.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of BackfillService.
 *
 * <p>Executes backfill jobs by:
 * <ol>
 *   <li>Creating custom TimeWindow for backfill range</li>
 *   <li>Building SQL query with time placeholders</li>
 *   <li>Executing query against source database</li>
 *   <li>Transforming results to SignalsHistory</li>
 *   <li>Purging existing data (if strategy is PURGE_AND_RELOAD)</li>
 *   <li>Ingesting data to signals_history table</li>
 * </ol>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultBackfillService implements BackfillService {

    private final BackfillJobRepository backfillJobRepository;
    private final LoaderRepository loaderRepository;
    private final SignalsHistoryRepository signalsHistoryRepository;
    private final ReplicaNameProvider replicaNameProvider;

    // Pipeline components
    private final QueryParameterReplacer queryParameterReplacer;
    private final DataTransformer dataTransformer;
    private final SourceDbManager sourceDbManager;

    /**
     * Submits a new backfill job.
     *
     * @param loaderCode Loader code
     * @param fromTime Start time for backfill range
     * @param toTime End time for backfill range
     * @param purgeStrategy Strategy for handling existing data
     * @param requestedBy User requesting the backfill
     * @return Created backfill job
     * @throws BusinessException if validation fails or loader not found
     */
    @Override
    @Transactional
    public BackfillJob submitBackfillJob(String loaderCode,
                                          Instant fromTime,
                                          Instant toTime,
                                          PurgeStrategy purgeStrategy,
                                          String requestedBy) {
        MDC.put("loaderCode", loaderCode);

        try {
            log.info("Submitting backfill job | loaderCode={} | fromTime={} | toTime={} | strategy={}",
                loaderCode, fromTime, toTime, purgeStrategy);

            // Validate loader code
            if (loaderCode == null || loaderCode.isBlank()) {
                log.warn("Loader code is null or blank");
                throw new BusinessException(
                    ErrorCode.VALIDATION_REQUIRED_FIELD,
                    "Loader code is required",
                    "loaderCode"
                );
            }

            // Validate loader exists
            Loader loader = loaderRepository.findByLoaderCode(loaderCode)
                .orElseThrow(() -> {
                    log.warn("Loader not found: {}", loaderCode);
                    return new BusinessException(
                        ErrorCode.LOADER_NOT_FOUND,
                        "Loader with code '" + loaderCode + "' not found"
                    );
                });

            // Validate time range
            if (fromTime == null || toTime == null) {
                log.warn("Time range is null | fromTime={} | toTime={}", fromTime, toTime);
                throw new BusinessException(
                    ErrorCode.BACKFILL_INVALID_TIME_RANGE,
                    "From time and to time are required"
                );
            }

            if (!toTime.isAfter(fromTime)) {
                log.warn("Invalid time range | fromTime={} | toTime={}", fromTime, toTime);
                throw new BusinessException(
                    ErrorCode.BACKFILL_INVALID_TIME_RANGE,
                    "To time must be after from time"
                );
            }

            log.debug("Validation passed for backfill job submission");

            // Create backfill job
            BackfillJob job = BackfillJob.builder()
                .loaderCode(loaderCode)
                .fromTimeEpoch(fromTime.getEpochSecond())
                .toTimeEpoch(toTime.getEpochSecond())
                .purgeStrategy(purgeStrategy != null ? purgeStrategy : PurgeStrategy.PURGE_AND_RELOAD)
                .status(BackfillJobStatus.PENDING)
                .requestedBy(requestedBy)
                .requestedAt(Instant.now())
                .build();

            BackfillJob saved = backfillJobRepository.save(job);

            log.info("Backfill job submitted | jobId={} | loaderCode={} | timeRange=[{}, {}] | strategy={}",
                saved.getId(), loaderCode, fromTime, toTime, saved.getPurgeStrategy());

            return saved;

        } finally {
            MDC.remove("loaderCode");
        }
    }

    /**
     * Executes a backfill job.
     *
     * @param jobId Backfill job ID
     * @return Updated backfill job with execution results
     * @throws BusinessException if job not found or not in PENDING status
     */
    @Override
    @Transactional
    public BackfillJob executeBackfillJob(Long jobId) {
        MDC.put("backfillJobId", jobId.toString());

        try {
            log.info("Executing backfill job | jobId={}", jobId);

            // Load job
            BackfillJob job = backfillJobRepository.findById(jobId)
                .orElseThrow(() -> {
                    log.warn("Backfill job not found | jobId={}", jobId);
                    return new BusinessException(
                        ErrorCode.BACKFILL_JOB_NOT_FOUND,
                        "Backfill job with ID " + jobId + " not found"
                    );
                });

            MDC.put("loaderCode", job.getLoaderCode());

            // Validate status
            if (job.getStatus() != BackfillJobStatus.PENDING) {
                log.warn("Backfill job not in PENDING status | jobId={} | currentStatus={}",
                    jobId, job.getStatus());
                throw new BusinessException(
                    ErrorCode.BACKFILL_JOB_NOT_PENDING,
                    "Backfill job must be in PENDING status. Current status: " + job.getStatus()
                );
            }

            // Load loader
            Loader loader = loaderRepository.findByLoaderCode(job.getLoaderCode())
                .orElseThrow(() -> {
                    log.warn("Loader not found for backfill job | loaderCode={}", job.getLoaderCode());
                    return new BusinessException(
                        ErrorCode.LOADER_NOT_FOUND,
                        "Loader with code '" + job.getLoaderCode() + "' not found"
                    );
                });

            log.info("Starting backfill execution | jobId={} | loaderCode={} | replica={} | timeRange=[{}, {}]",
                jobId, loader.getLoaderCode(), replicaNameProvider.getReplicaName(),
                job.getFromTimeInstant(), job.getToTimeInstant());

            Instant startTime = Instant.now();

            // Mark job as RUNNING
            job.setStatus(BackfillJobStatus.RUNNING);
            job.setStartTime(startTime);
            job.setReplicaName(replicaNameProvider.getReplicaName());
            backfillJobRepository.save(job);

            log.debug("Backfill job marked as RUNNING");

            try {
                // Execute backfill
                BackfillResult result = executeBackfillReal(loader, job);

                // Update job with success
                Instant endTime = Instant.now();
                long durationSeconds = Duration.between(startTime, endTime).getSeconds();

                job.setStatus(BackfillJobStatus.SUCCESS);
                job.setEndTime(endTime);
                job.setDurationSeconds(durationSeconds);
                job.setRecordsPurged(result.recordsPurged);
                job.setRecordsLoaded(result.recordsLoaded);
                job.setRecordsIngested(result.recordsIngested);

                backfillJobRepository.save(job);

                log.info("Backfill job completed | jobId={} | loaderCode={} | duration={}s | purged={} | loaded={} | ingested={}",
                    jobId, loader.getLoaderCode(), durationSeconds,
                    result.recordsPurged, result.recordsLoaded, result.recordsIngested);

                return job;

            } catch (Exception e) {
                log.error("Backfill job execution failed | jobId={} | loaderCode={}",
                    jobId, loader.getLoaderCode(), e);

                // Update job with failure
                Instant endTime = Instant.now();
                long durationSeconds = Duration.between(startTime, endTime).getSeconds();

                job.setStatus(BackfillJobStatus.FAILED);
                job.setEndTime(endTime);
                job.setDurationSeconds(durationSeconds);
                job.setErrorMessage(e.getMessage());
                job.setStackTrace(getStackTraceAsString(e));

                backfillJobRepository.save(job);

                log.warn("Backfill job marked as FAILED | jobId={} | duration={}s",
                    jobId, durationSeconds);

                return job;
            }

        } finally {
            MDC.remove("backfillJobId");
            MDC.remove("loaderCode");
        }
    }

    /**
     * Gets a backfill job by ID.
     *
     * @param jobId Job ID
     * @return Optional containing the job if found
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<BackfillJob> getBackfillJob(Long jobId) {
        log.debug("Fetching backfill job | jobId={}", jobId);
        return backfillJobRepository.findById(jobId);
    }

    /**
     * Gets all backfill jobs for a loader.
     *
     * @param loaderCode Loader code
     * @return List of backfill jobs ordered by requested time (newest first)
     */
    @Override
    @Transactional(readOnly = true)
    public List<BackfillJob> getBackfillJobsByLoader(String loaderCode) {
        log.debug("Fetching backfill jobs by loader | loaderCode={}", loaderCode);
        return backfillJobRepository.findByLoaderCodeOrderByRequestedAtDesc(loaderCode);
    }

    /**
     * Gets all backfill jobs with a specific status.
     *
     * @param status Job status
     * @return List of backfill jobs with the given status
     */
    @Override
    @Transactional(readOnly = true)
    public List<BackfillJob> getBackfillJobsByStatus(BackfillJobStatus status) {
        log.debug("Fetching backfill jobs by status | status={}", status);
        return backfillJobRepository.findByStatusOrderByRequestedAtDesc(status);
    }

    /**
     * Gets recent backfill jobs.
     *
     * @param limit Maximum number of jobs to return
     * @return List of recent backfill jobs
     */
    @Override
    @Transactional(readOnly = true)
    public List<BackfillJob> getRecentBackfillJobs(int limit) {
        log.debug("Fetching recent backfill jobs | limit={}", limit);
        return backfillJobRepository.findRecentJobs(limit);
    }

    /**
     * Cancels a pending backfill job.
     *
     * @param jobId Job ID to cancel
     * @return Cancelled job
     * @throws BusinessException if job not found or not in PENDING status
     */
    @Override
    @Transactional
    public BackfillJob cancelBackfillJob(Long jobId) {
        MDC.put("backfillJobId", jobId.toString());

        try {
            log.info("Cancelling backfill job | jobId={}", jobId);

            BackfillJob job = backfillJobRepository.findById(jobId)
                .orElseThrow(() -> {
                    log.warn("Backfill job not found | jobId={}", jobId);
                    return new BusinessException(
                        ErrorCode.BACKFILL_JOB_NOT_FOUND,
                        "Backfill job with ID " + jobId + " not found"
                    );
                });

            MDC.put("loaderCode", job.getLoaderCode());

            if (job.getStatus() != BackfillJobStatus.PENDING) {
                log.warn("Cannot cancel: job not in PENDING status | jobId={} | currentStatus={}",
                    jobId, job.getStatus());
                throw new BusinessException(
                    ErrorCode.BACKFILL_JOB_NOT_PENDING,
                    "Only PENDING jobs can be cancelled. Current status: " + job.getStatus()
                );
            }

            job.setStatus(BackfillJobStatus.CANCELLED);
            job.setEndTime(Instant.now());

            BackfillJob saved = backfillJobRepository.save(job);

            log.info("Backfill job cancelled | jobId={} | loaderCode={}", jobId, job.getLoaderCode());

            return saved;

        } finally {
            MDC.remove("backfillJobId");
            MDC.remove("loaderCode");
        }
    }

    /**
     * Counts active backfill jobs (PENDING or RUNNING).
     *
     * @return Number of active jobs
     */
    @Override
    @Transactional(readOnly = true)
    public long countActiveJobs() {
        log.debug("Counting active backfill jobs");
        return backfillJobRepository.countActiveJobs();
    }

    /**
     * Counts active backfill jobs for a specific loader.
     *
     * @param loaderCode Loader code
     * @return Number of active jobs for the loader
     */
    @Override
    @Transactional(readOnly = true)
    public long countActiveJobsByLoader(String loaderCode) {
        log.debug("Counting active backfill jobs | loaderCode={}", loaderCode);
        return backfillJobRepository.countActiveJobsByLoader(loaderCode);
    }

    /**
     * Executes backfill for a specific time range.
     *
     * <p>Pipeline:
     * <ol>
     *   <li>Create TimeWindow from job range</li>
     *   <li>Build executable SQL</li>
     *   <li>Execute query against source database</li>
     *   <li>Transform results</li>
     *   <li>Apply purge strategy</li>
     *   <li>Ingest data</li>
     * </ol>
     */
    private BackfillResult executeBackfillReal(Loader loader, BackfillJob job) throws Exception {
        String loaderCode = loader.getLoaderCode();
        Instant fromTime = job.getFromTimeInstant();
        Instant toTime = job.getToTimeInstant();

        log.info("Executing backfill for {} (range: {} to {}, strategy: {})",
            loaderCode, fromTime, toTime, job.getPurgeStrategy());

        // Step 1: Create TimeWindow from backfill range
        TimeWindow window = new TimeWindow(fromTime, toTime);

        // Step 2: Build executable SQL (with timezone handling)
        String loaderSql = loader.getLoaderSql(); // Auto-decrypted
        Integer timezoneOffset = loader.getSourceTimezoneOffsetHours();
        String executableSql = queryParameterReplacer.replacePlaceholders(loaderSql, window, timezoneOffset);

        log.info("Built executable SQL for backfill (timezone offset: {} hours): {}",
            timezoneOffset != null ? timezoneOffset : 0, executableSql);

        // Step 3: Execute query against source database
        String sourceDbCode = loader.getSourceDatabase().getDbCode();
        List<Map<String, Object>> rows = sourceDbManager.runQuery(sourceDbCode, executableSql);

        log.info("Query executed for backfill: {} rows returned from source DB '{}'",
            rows.size(), sourceDbCode);

        // Build LoaderQueryResult
        LoaderQueryResult queryResult = new LoaderQueryResult(
            window.fromTime(),
            window.toTime(),
            rows,
            rows.size()
        );

        // Step 4: Transform results (with timezone normalization)
        List<SignalsHistory> signals = dataTransformer.transform(loaderCode, queryResult, timezoneOffset);

        log.info("Transformed {} rows for backfill: {} SignalsHistory entities created",
            rows.size(), signals.size());

        // Step 5: Apply purge strategy
        long recordsPurged = applyPurgeStrategy(job.getPurgeStrategy(), loaderCode, fromTime, toTime);

        log.info("Purge strategy {} applied: {} records purged",
            job.getPurgeStrategy(), recordsPurged);

        // Step 6: Ingest to signals_history table
        List<SignalsHistory> ingested = signalsHistoryRepository.saveAll(signals);

        log.info("Ingested {} signals for backfill to signals_history table", ingested.size());

        // Return result
        return new BackfillResult(
            recordsPurged,
            (long) rows.size(),
            (long) ingested.size()
        );
    }

    /**
     * Applies purge strategy for backfill time range.
     *
     * @param strategy   Purge strategy
     * @param loaderCode Loader code
     * @param fromTime   Start of time range
     * @param toTime     End of time range
     * @return Number of records purged
     * @throws BusinessException if FAIL_ON_DUPLICATE and data exists
     */
    private long applyPurgeStrategy(PurgeStrategy strategy,
                                     String loaderCode,
                                     Instant fromTime,
                                     Instant toTime) {
        long fromEpoch = fromTime.getEpochSecond();
        long toEpoch = toTime.getEpochSecond();

        log.debug("Applying purge strategy | strategy={} | loaderCode={} | timeRange=[{}, {}]",
            strategy, loaderCode, fromEpoch, toEpoch);

        switch (strategy) {
            case PURGE_AND_RELOAD:
                // Delete existing data in range
                long deleted = signalsHistoryRepository.deleteByLoaderCodeAndLoadTimeStampBetween(
                    loaderCode, fromEpoch, toEpoch);
                log.info("PURGE_AND_RELOAD applied | loaderCode={} | deleted={} | timeRange=[{}, {}]",
                    loaderCode, deleted, fromEpoch, toEpoch);
                return deleted;

            case FAIL_ON_DUPLICATE:
                // Check if data exists
                List<SignalsHistory> existing = signalsHistoryRepository
                    .findByLoaderCodeAndLoadTimeStampBetween(loaderCode, fromEpoch, toEpoch);
                if (!existing.isEmpty()) {
                    log.warn("FAIL_ON_DUPLICATE: duplicate data found | loaderCode={} | existingRecords={} | timeRange=[{}, {}]",
                        loaderCode, existing.size(), fromEpoch, toEpoch);
                    throw new BusinessException(
                        ErrorCode.BACKFILL_DUPLICATE_DATA,
                        String.format("Found %d existing records for %s in range [%d, %d]",
                            existing.size(), loaderCode, fromEpoch, toEpoch)
                    );
                }
                log.debug("FAIL_ON_DUPLICATE: no duplicates found");
                return 0;

            case SKIP_DUPLICATES:
                // Do nothing - duplicates will be skipped at insert (or fail with unique constraint)
                log.debug("SKIP_DUPLICATES: will skip any duplicate records during insert");
                return 0;

            default:
                log.error("Unknown purge strategy | strategy={}", strategy);
                throw new BusinessException(
                    ErrorCode.VALIDATION_INVALID_VALUE,
                    "Unknown purge strategy: " + strategy,
                    "purgeStrategy"
                );
        }
    }

    /**
     * Converts exception to stack trace string.
     */
    private String getStackTraceAsString(Exception exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Result of backfill execution (internal DTO).
     */
    private record BackfillResult(long recordsPurged, long recordsLoaded, long recordsIngested) {
    }
}
