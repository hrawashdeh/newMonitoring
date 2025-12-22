package com.tiqmo.monitoring.loader.service.scheduler;

import com.tiqmo.monitoring.loader.domain.loader.entity.LoadExecutionStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoadHistory;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.PurgeStrategy;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoadHistoryRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.service.backfill.BackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Automated Gap Scanner Service - Detects and fills data gaps.
 *
 * <p><b>Gap Detection Scenarios:</b>
 * <ul>
 *   <li><b>Partial Load:</b> actual_from_time != query_from_time OR actual_to_time != query_to_time</li>
 *   <li><b>No Data Loaded:</b> actual_from_time IS NULL (zero records loaded despite query success)</li>
 *   <li><b>Timeline Gaps:</b> Gap between consecutive successful loads (previous actual_to_time < current actual_from_time)</li>
 * </ul>
 *
 * <p><b>Recovery Strategy:</b>
 * Automatically submits backfill jobs with PURGE_AND_RELOAD for detected gaps.
 *
 * <p><b>Schedule:</b> Runs every 6 hours to detect and recover gaps.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GapScannerService {

    private final LoadHistoryRepository loadHistoryRepository;
    private final LoaderRepository loaderRepository;
    private final BackfillService backfillService;

    /**
     * Minimum gap size to trigger backfill (5 minutes).
     * Prevents backfill spam for tiny gaps due to clock skew or rounding.
     */
    private static final Duration MIN_GAP_SIZE = Duration.ofMinutes(5);

    /**
     * Lookback window for gap detection (7 days).
     * Only scans recent history to avoid overwhelming backfill queue.
     */
    private static final Duration GAP_SCAN_LOOKBACK = Duration.ofDays(7);

    /**
     * Gap scanning scheduler - runs every 6 hours.
     *
     * <p>Scans load_history for gaps in the last 7 days and submits backfill jobs.
     */
    @Scheduled(cron = "0 0 */6 * * ?") // Every 6 hours
    public void scanForGaps() {
        try {
            log.info("Gap Scanner: Starting automated gap detection scan");

            Instant scanSince = Instant.now().minus(GAP_SCAN_LOOKBACK);
            int gapsDetected = 0;
            int backfillsSubmitted = 0;

            // Scan each enabled loader
            List<Loader> loaders = loaderRepository.findAllByEnabledTrue();
            log.debug("Gap Scanner: Scanning {} enabled loaders for gaps since {}", loaders.size(), scanSince);

            for (Loader loader : loaders) {
                try {
                    int loaderGaps = scanLoaderForGaps(loader, scanSince);
                    if (loaderGaps > 0) {
                        gapsDetected += loaderGaps;
                        backfillsSubmitted += loaderGaps;
                        log.info("Gap Scanner: Detected and submitted {} gap(s) for loader {}",
                            loaderGaps, loader.getLoaderCode());
                    }
                } catch (Exception e) {
                    log.error("Gap Scanner: Error scanning loader {}: {}",
                        loader.getLoaderCode(), e.getMessage(), e);
                    // Continue with next loader
                }
            }

            if (gapsDetected > 0) {
                log.warn("Gap Scanner: Detected {} gap(s) across {} loaders, submitted {} backfill job(s)",
                    gapsDetected, loaders.size(), backfillsSubmitted);
            } else {
                log.info("Gap Scanner: No gaps detected in recent history");
            }

        } catch (Exception e) {
            log.error("Gap Scanner: Unexpected error during gap scan", e);
        }
    }

    /**
     * Scans a single loader for gaps in recent history.
     *
     * <p>Detects three types of gaps:
     * <ol>
     *   <li><b>Partial Load:</b> Queried time range larger than actual loaded range</li>
     *   <li><b>Zero Record Load:</b> Query succeeded but no data loaded (actual_from_time IS NULL)</li>
     *   <li><b>Timeline Gap:</b> Gap between consecutive successful loads</li>
     * </ol>
     *
     * @param loader Loader to scan
     * @param scanSince Only scan history since this timestamp
     * @return Number of gaps detected and backfills submitted
     */
    private int scanLoaderForGaps(Loader loader, Instant scanSince) {
        String loaderCode = loader.getLoaderCode();
        int gapsFound = 0;

        // Get recent load history for this loader (ordered by start time)
        List<LoadHistory> history = loadHistoryRepository
            .findByLoaderCodeAndStartTimeAfter(loaderCode, scanSince);

        if (history.isEmpty()) {
            log.trace("Gap Scanner: No recent history for loader {}", loaderCode);
            return 0;
        }

        log.debug("Gap Scanner: Analyzing {} history records for loader {}", history.size(), loaderCode);

        LoadHistory previousLoad = null;

        for (LoadHistory currentLoad : history) {
            // Only analyze SUCCESS loads (FAILED loads already trigger auto-backfill)
            if (currentLoad.getStatus() != LoadExecutionStatus.SUCCESS) {
                previousLoad = currentLoad;
                continue;
            }

            // Scenario 1: Partial Load Detection
            // Check if actual loaded range is smaller than queried range
            if (currentLoad.getActualFromTime() != null && currentLoad.getActualToTime() != null) {
                Instant queryFrom = currentLoad.getQueryFromTime();
                Instant queryTo = currentLoad.getQueryToTime();
                Instant actualFrom = currentLoad.getActualFromTime();
                Instant actualTo = currentLoad.getActualToTime();

                // Check for gap at start of window
                Duration startGap = Duration.between(queryFrom, actualFrom);
                if (startGap.compareTo(MIN_GAP_SIZE) > 0) {
                    log.warn("Gap Scanner: Detected START gap for {} | queried: {} | actual: {} | gap: {} minutes",
                        loaderCode, queryFrom, actualFrom, startGap.toMinutes());

                    submitGapBackfill(loaderCode, queryFrom, actualFrom, "START_GAP");
                    gapsFound++;
                }

                // Check for gap at end of window
                Duration endGap = Duration.between(actualTo, queryTo);
                if (endGap.compareTo(MIN_GAP_SIZE) > 0) {
                    log.warn("Gap Scanner: Detected END gap for {} | actual: {} | queried: {} | gap: {} minutes",
                        loaderCode, actualTo, queryTo, endGap.toMinutes());

                    submitGapBackfill(loaderCode, actualTo, queryTo, "END_GAP");
                    gapsFound++;
                }
            }

            // Scenario 2: Zero Record Load (no actual data despite successful query)
            if (currentLoad.getActualFromTime() == null && currentLoad.getActualToTime() == null
                && currentLoad.getRecordsLoaded() != null && currentLoad.getRecordsLoaded() == 0) {
                log.debug("Gap Scanner: Loader {} had zero-record load for window {} to {} (possible downtime, skipping gap backfill)",
                    loaderCode, currentLoad.getQueryFromTime(), currentLoad.getQueryToTime());
                // NOTE: Don't submit backfill for zero-record loads - likely source downtime
                // The loader will naturally retry this window on next execution
            }

            // Scenario 3: Timeline Gap Detection (gap between consecutive successful loads)
            if (previousLoad != null
                && previousLoad.getStatus() == LoadExecutionStatus.SUCCESS
                && previousLoad.getActualToTime() != null
                && currentLoad.getActualFromTime() != null) {

                Duration timelineGap = Duration.between(previousLoad.getActualToTime(), currentLoad.getActualFromTime());

                if (timelineGap.compareTo(MIN_GAP_SIZE) > 0) {
                    log.warn("Gap Scanner: Detected TIMELINE gap for {} | previous ended: {} | current started: {} | gap: {} minutes",
                        loaderCode, previousLoad.getActualToTime(), currentLoad.getActualFromTime(), timelineGap.toMinutes());

                    submitGapBackfill(loaderCode, previousLoad.getActualToTime(), currentLoad.getActualFromTime(), "TIMELINE_GAP");
                    gapsFound++;
                }
            }

            previousLoad = currentLoad;
        }

        return gapsFound;
    }

    /**
     * Submits a backfill job for a detected gap.
     *
     * @param loaderCode Loader code
     * @param fromTime Gap start time
     * @param toTime Gap end time
     * @param reason Gap detection reason (for logging)
     */
    private void submitGapBackfill(String loaderCode, Instant fromTime, Instant toTime, String reason) {
        try {
            // Check if backfill already exists for this window
            long activeJobs = backfillService.countActiveJobsByLoader(loaderCode);
            if (activeJobs > 5) {
                log.warn("Gap Scanner: Skipping backfill for {} - already has {} active backfill jobs",
                    loaderCode, activeJobs);
                return;
            }

            log.info("Gap Scanner: Submitting backfill for {} | reason: {} | window: {} to {}",
                loaderCode, reason, fromTime, toTime);

            backfillService.submitBackfillJob(
                loaderCode,
                fromTime,
                toTime,
                PurgeStrategy.PURGE_AND_RELOAD,
                "SYSTEM_GAP_SCANNER_" + reason
            );

            log.info("Gap Scanner: Backfill submitted successfully for {} ({})", loaderCode, reason);

        } catch (Exception e) {
            log.error("Gap Scanner: Failed to submit backfill for {} ({}): {}",
                loaderCode, reason, e.getMessage(), e);
        }
    }
}
