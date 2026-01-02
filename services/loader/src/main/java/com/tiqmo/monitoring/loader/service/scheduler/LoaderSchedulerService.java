package com.tiqmo.monitoring.loader.service.scheduler;

import com.tiqmo.monitoring.loader.domain.loader.entity.ApprovalStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoadStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoadHistoryRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderExecutionLockRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.infra.config.ExecutionProperties;
import com.tiqmo.monitoring.loader.infra.config.LockingProperties;
import com.tiqmo.monitoring.loader.service.execution.LoadExecutorService;
import com.tiqmo.monitoring.loader.service.locking.LoaderLock;
import com.tiqmo.monitoring.loader.service.locking.LockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Loader Scheduler Service - Coordinates automated loader execution.
 *
 * <p><b>Rounds 10-13, 15 Implementation:</b>
 * <ul>
 *   <li><b>Round 10:</b> Basic scheduling - find enabled loaders, check eligibility, execute</li>
 *   <li><b>Round 11:</b> Priority scheduling - IDLE loaders before RUNNING/FAILED/PAUSED</li>
 *   <li><b>Round 12:</b> Auto-recovery - reset FAILED loaders after 20 minutes</li>
 *   <li><b>Round 13:</b> Stale lock cleanup - release abandoned locks from crashed pods</li>
 *   <li><b>Round 15:</b> Pause support - skip PAUSED loaders during execution</li>
 * </ul>
 *
 * <p><b>Execution Flow:</b>
 * <pre>
 * 1. Recover FAILED loaders (if failedSince + 20 minutes < now)
 * 2. Find all enabled loaders
 * 3. Sort by priority (IDLE > RUNNING > FAILED)
 * 4. For each loader:
 *    a. Check if due for execution (based on lastLoadTimestamp + minIntervalSeconds)
 *    b. Try to acquire lock (prevents duplicate execution across replicas)
 *    c. If lock acquired, execute via LoadExecutorService
 *    d. Release lock
 * </pre>
 *
 * <p><b>Scheduling Configuration:</b>
 * <ul>
 *   <li>Main scheduler: Runs every 10 seconds (fixedDelay = 10000ms)</li>
 *   <li>Lock cleanup: Runs every 30 minutes (fixedDelay = 1800000ms)</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0 (Rounds 10-13, 15)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoaderSchedulerService {

  private final LoaderRepository loaderRepository;
  private final LoadHistoryRepository loadHistoryRepository;
  private final LoaderExecutionLockRepository lockRepository;
  private final LockManager lockManager;
  private final LoadExecutorService loadExecutorService;

  @Qualifier("loaderExecutorService")
  private final ExecutorService executorService;

  private final ExecutionProperties executionProperties;
  private final LockingProperties lockingProperties;

  private final com.tiqmo.monitoring.loader.domain.signals.repo.SignalsHistoryRepository signalsHistoryRepository;

  /**
   * Round 12: Auto-recovery threshold for FAILED loaders (20 minutes).
   */
  private static final Duration FAILED_RECOVERY_THRESHOLD = Duration.ofMinutes(20);

  /**
   * Load history retention period (30 days).
   * Older records are deleted to maintain query performance.
   */
  private static final int LOAD_HISTORY_RETENTION_DAYS = 30;

  /**
   * Main scheduling loop - runs every 10 seconds.
   *
   * <p>Coordinates Rounds 10-12:
   * <ul>
   *   <li>Round 12: Recover FAILED loaders</li>
   *   <li>Round 10: Basic scheduling logic</li>
   *   <li>Round 11: Priority-based execution</li>
   * </ul>
   *
   * <p><b>SECURITY:</b> Only executes APPROVED loaders. PENDING_APPROVAL and
   * REJECTED loaders are skipped to prevent unauthorized code execution.
   */
  @Scheduled(fixedDelay = 10000, initialDelay = 5000)
  public void scheduleLoaders() {
    try {
      log.trace("Entering scheduleLoaders() | processId={} | contextId={}",
              MDC.get("processId"), MDC.get("contextId"));
      log.debug("Scheduler: Starting scheduling cycle");

      // Round 12: Auto-recover FAILED loaders
      log.trace("Executing recoverFailedLoaders()");
      recoverFailedLoaders();

      // SECURITY: Find all enabled AND APPROVED loaders only
      log.trace("Querying for enabled and approved loaders");
      List<Loader> enabledLoaders = loaderRepository.findAllByEnabledTrueAndApprovalStatus(ApprovalStatus.APPROVED);
      if (enabledLoaders.isEmpty()) {
        log.debug("Scheduler: No enabled and approved loaders found");
        log.trace("Exiting scheduleLoaders() | reason=no_loaders");
        return;
      }

      log.debug("Scheduler: Found {} enabled and approved loader(s)", enabledLoaders.size());
      log.trace("Loader codes: {}", enabledLoaders.stream().map(Loader::getLoaderCode).toList());

      // Round 11: Sort by priority (IDLE > RUNNING > FAILED)
      log.trace("Sorting loaders by priority (IDLE > RUNNING > FAILED)");
      List<Loader> sortedLoaders = sortByPriority(enabledLoaders);

      // Round 10: Process each loader
      int processed = 0, executed = 0, skipped = 0;
      for (Loader loader : sortedLoaders) {
        MDC.put("loaderCode", loader.getLoaderCode());
        try {
          log.trace("Processing loader {} (status: {}, lastLoad: {})",
                  loader.getLoaderCode(), loader.getLoadStatus(), loader.getLastLoadTimestamp());
          processLoader(loader);
          processed++;
          if (loader.getLoadStatus() == LoadStatus.RUNNING) {
            executed++;
          } else {
            skipped++;
          }
        } catch (Exception e) {
          log.error("Scheduler: Error processing loader {} | correlationId={} | error={}",
              loader.getLoaderCode(), MDC.get("correlationId"), e.getMessage(), e);
          // Continue with next loader
        } finally {
          MDC.remove("loaderCode");
        }
      }

      log.info("Scheduler: Scheduling cycle complete | processed={} | executed={} | skipped={}",
              processed, executed, skipped);
      log.trace("Exiting scheduleLoaders() | success=true");

    } catch (Exception e) {
      log.error("Scheduler: Unexpected error in scheduling cycle | processId={}",
              MDC.get("processId"), e);
    }
  }

  /**
   * Round 13: Stale Lock Cleanup - runs every 30 minutes.
   *
   * <p>Releases locks that were acquired but never released due to pod crashes.
   * The LockManager will release locks older than 2 hours.
   *
   * <p>This is a safety mechanism to prevent abandoned locks from blocking
   * loader execution indefinitely when pods crash or are terminated abruptly.
   */
  @Scheduled(fixedDelay = 1800000, initialDelay = 60000)  // 30 minutes, 1 minute initial delay
  public void cleanupStaleLocks() {
    try {
      log.trace("Entering cleanupStaleLocks() | processId={}", MDC.get("processId"));
      log.debug("Round 13: Starting stale lock cleanup");

      log.trace("Invoking lockManager.cleanupStaleLocks()");
      int cleaned = lockManager.cleanupStaleLocks();

      if (cleaned > 0) {
        log.warn("Round 13: Cleaned up {} stale lock(s) from crashed/terminated pods | processId={}",
                cleaned, MDC.get("processId"));
      } else {
        log.debug("Round 13: No stale locks found");
      }

      log.trace("Exiting cleanupStaleLocks() | cleaned={} | success=true", cleaned);

    } catch (Exception e) {
      log.error("Round 13: Error during stale lock cleanup | processId={}", MDC.get("processId"), e);
    }
  }

  /**
   * Round 12: Auto-Recovery from FAILED status.
   *
   * <p>Resets FAILED loaders to IDLE if they've been FAILED for more than 20 minutes.
   * This allows automatic retry after a cooling-off period.
   *
   * <p>Package-private for integration testing.
   */
  public void recoverFailedLoaders() {
    log.trace("Entering recoverFailedLoaders() | processId={}", MDC.get("processId"));

    log.trace("Fetching all loaders from repository");
    List<Loader> allLoaders = loaderRepository.findAll();
    Instant now = Instant.now();

    int recovered = 0;
    int stillFailed = 0;

    for (Loader loader : allLoaders) {
      if (loader.getLoadStatus() == LoadStatus.FAILED && loader.getFailedSince() != null) {
        MDC.put("loaderCode", loader.getLoaderCode());
        try {
          Duration failedDuration = Duration.between(loader.getFailedSince(), now);
          log.trace("Checking FAILED loader {} | failedSince={} | failedDuration={}min",
                  loader.getLoaderCode(), loader.getFailedSince(), failedDuration.toMinutes());

          if (failedDuration.compareTo(FAILED_RECOVERY_THRESHOLD) > 0) {
            log.info("Round 12: Auto-recovering FAILED loader {} (failed for {} minutes) | processId={}",
                loader.getLoaderCode(), failedDuration.toMinutes(), MDC.get("processId"));

            loader.setLoadStatus(LoadStatus.IDLE);
            loader.setFailedSince(null);
            loaderRepository.save(loader);
            recovered++;

            log.info("Round 12: Loader {} reset to IDLE and ready for execution | processId={}",
                loader.getLoaderCode(), MDC.get("processId"));
          } else {
            stillFailed++;
            log.trace("Loader {} still within recovery threshold ({}min remaining)",
                    loader.getLoaderCode(),
                    FAILED_RECOVERY_THRESHOLD.minus(failedDuration).toMinutes());
          }
        } finally {
          MDC.remove("loaderCode");
        }
      }
    }

    if (recovered > 0 || stillFailed > 0) {
      log.debug("Round 12: Recovery summary | recovered={} | stillFailed={}", recovered, stillFailed);
    }
    log.trace("Exiting recoverFailedLoaders() | recovered={} | success=true", recovered);
  }

  /**
   * Round 11: Priority Scheduling.
   *
   * <p>Sorts loaders by status priority:
   * <ol>
   *   <li><b>IDLE</b> - Highest priority (ready to run)</li>
   *   <li><b>RUNNING</b> - Medium priority (currently executing, may have completed)</li>
   *   <li><b>FAILED</b> - Low priority (waiting for auto-recovery)</li>
   *   <li><b>PAUSED</b> - Lowest priority (manually paused, won't execute)</li>
   * </ol>
   *
   * @param loaders unsorted list of loaders
   * @return sorted list (IDLE first, PAUSED last)
   */
  private List<Loader> sortByPriority(List<Loader> loaders) {
    return loaders.stream()
        .sorted(Comparator.comparing(this::getStatusPriority))
        .toList();
  }

  /**
   * Helper method for priority sorting (lower number = higher priority).
   */
  private int getStatusPriority(Loader loader) {
    return switch (loader.getLoadStatus()) {
      case IDLE -> 1;      // Highest priority
      case RUNNING -> 2;   // Medium priority
      case FAILED -> 3;    // Low priority
      case PAUSED -> 4;    // Lowest priority (won't execute)
    };
  }

  /**
   * Round 10: Process a single loader.
   *
   * <p>Checks if loader is due for execution and attempts to execute if eligible.
   * Executes in thread pool with configured timeout.
   *
   * @param loader the loader to process
   */
  private void processLoader(Loader loader) {
    String loaderCode = loader.getLoaderCode();

    // Round 15: Skip PAUSED loaders
    if (loader.getLoadStatus() == LoadStatus.PAUSED) {
      log.trace("Scheduler: Loader {} is PAUSED, skipping execution", loaderCode);
      return;
    }

    // Check if loader is due for execution
    if (!isDueForExecution(loader)) {
      log.trace("Scheduler: Loader {} not due for execution yet", loaderCode);
      return;
    }

    // Try to acquire lock
    Optional<LoaderLock> lockOpt = lockManager.tryAcquireLock(loader);
    if (lockOpt.isEmpty()) {
      log.debug("Scheduler: Could not acquire lock for loader {} (already locked)", loaderCode);
      return;
    }

    LoaderLock lock = lockOpt.get();
    String lockId = lock.getLockId();

    try {
      log.info("Scheduler: Executing loader {} (status: {}, last run: {})",
          loaderCode, loader.getLoadStatus(), loader.getLastLoadTimestamp());

      // Submit execution to thread pool with timeout
      Future<?> future = executorService.submit(() -> {
        try {
          loadExecutorService.executeLoader(loader);
        } catch (Exception e) {
          log.error("Loader execution failed: {}", loaderCode, e);
          throw new RuntimeException(e);
        }
      });

      // Register thread for potential cancellation
      lockManager.registerExecution(lockId, future);

      // Wait with timeout (configurable from application.yaml)
      int timeoutHours = executionProperties.getExecutionTimeoutHours();
      try {
        future.get(timeoutHours, TimeUnit.HOURS);
        log.info("Scheduler: Successfully executed loader {}", loaderCode);
      } catch (TimeoutException e) {
        log.error("Scheduler: Loader {} execution timed out after {} hours - cancelling",
            loaderCode, timeoutHours);
        future.cancel(true); // Interrupt the thread
      } catch (Exception e) {
        log.error("Scheduler: Loader {} execution failed: {}", loaderCode, e.getMessage(), e);
      }

    } finally {
      // Always unregister and release the lock
      lockManager.unregisterExecution(lockId);
      lockManager.releaseLock(lock);
      log.debug("Scheduler: Released lock for loader {}", loaderCode);
    }
  }

  /**
   * Round 10: Check if loader is due for execution.
   *
   * <p>A loader is due for execution if:
   * <ul>
   *   <li>Never executed before (lastLoadTimestamp == null), OR</li>
   *   <li>Time since last execution >= minIntervalSeconds</li>
   * </ul>
   *
   * @param loader the loader to check
   * @return true if loader should be executed now
   */
  private boolean isDueForExecution(Loader loader) {
    Instant lastLoadTimestamp = loader.getLastLoadTimestamp();

    // Never executed before - always due
    if (lastLoadTimestamp == null) {
      log.debug("Loader {} has never been executed - due for execution", loader.getLoaderCode());
      return true;
    }

    // Calculate time since last execution
    Instant now = Instant.now();
    long secondsSinceLastExecution = Duration.between(lastLoadTimestamp, now).getSeconds();
    long minIntervalSeconds = loader.getMinIntervalSeconds();

    boolean isDue = secondsSinceLastExecution >= minIntervalSeconds;

    if (isDue) {
      log.debug("Loader {} is due for execution ({}s since last run, min interval: {}s)",
          loader.getLoaderCode(), secondsSinceLastExecution, minIntervalSeconds);
    }

    return isDue;
  }

  /**
   * Cleanup old released locks - runs daily at configured time (default: 2 AM).
   *
   * <p>Deletes locks that have been released for longer than the configured
   * retention period (default: 7 days). This maintains query performance on
   * the loader_execution_lock table.
   *
   * <p>Schedule configured via {@code loader.locking.cleanup-schedule} in application.yaml.
   */
  @Scheduled(cron = "${loader.locking.cleanup-schedule:0 0 2 * * ?}")
  public void cleanupReleasedLocks() {
    try {
      int retentionDays = lockingProperties.getReleasedLockRetentionDays();
      Instant deleteBefore = Instant.now().minusSeconds(retentionDays * 86400L);

      log.debug("Starting cleanup of released locks older than {} days", retentionDays);

      long deleted = lockRepository.deleteByReleasedAndReleasedAtBefore(true, deleteBefore);

      if (deleted > 0) {
        log.info("Cleaned up {} released lock(s) older than {} days", deleted, retentionDays);
      } else {
        log.debug("No released locks found older than {} days", retentionDays);
      }

    } catch (Exception e) {
      log.error("Error during released locks cleanup", e);
    }
  }

  /**
   * Cleanup orphaned signals from FAILED loads - runs every hour.
   *
   * <p>Deletes signals_history records where load_history_id points to a FAILED load.
   * This prevents orphaned data from accumulating due to non-transactional execution.
   *
   * <p><b>Detection:</b> Direct FK relationship via load_history_id
   * <p><b>Safety:</b> Only deletes signals from confirmed FAILED loads
   * <p><b>Performance:</b> Uses indexed load_history_id column
   */
  @Scheduled(cron = "0 0 * * * ?") // Every hour at :00
  public void cleanupOrphanedSignals() {
    try {
      log.debug("Starting orphaned signals cleanup");

      // Method 1: Direct delete with subquery (most efficient)
      long deleted = signalsHistoryRepository.deleteByLoadHistoryIdInFailedLoads();

      if (deleted > 0) {
        log.warn("Cleaned up {} orphaned signal(s) from FAILED loads", deleted);
      } else {
        log.debug("No orphaned signals found from FAILED loads");
      }

    } catch (Exception e) {
      log.error("Error during orphaned signals cleanup", e);
    }
  }

  /**
   * Cleanup old load_history records - runs daily at 3 AM.
   *
   * <p>Deletes load_history records older than {@link #LOAD_HISTORY_RETENTION_DAYS}
   * (30 days) to maintain query performance.
   *
   * <p><b>NOTE:</b> Orphaned signals are cleaned up BEFORE this via {@link #cleanupOrphanedSignals()}
   *
   * <p>Retention period can be adjusted via the constant or made configurable.
   */
  @Scheduled(cron = "0 0 3 * * ?") // Daily at 3 AM
  public void cleanupLoadHistory() {
    try {
      Instant deleteBefore = Instant.now().minusSeconds(LOAD_HISTORY_RETENTION_DAYS * 86400L);

      log.debug("Starting cleanup of load_history records older than {} days",
          LOAD_HISTORY_RETENTION_DAYS);

      long deleted = loadHistoryRepository.deleteByStartTimeBefore(deleteBefore);

      if (deleted > 0) {
        log.info("Cleaned up {} load_history record(s) older than {} days",
            deleted, LOAD_HISTORY_RETENTION_DAYS);
      } else {
        log.debug("No load_history records found older than {} days",
            LOAD_HISTORY_RETENTION_DAYS);
      }

    } catch (Exception e) {
      log.error("Error during load_history cleanup", e);
    }
  }
}
