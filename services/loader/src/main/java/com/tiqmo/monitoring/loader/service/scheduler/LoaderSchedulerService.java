package com.tiqmo.monitoring.loader.service.scheduler;

import com.tiqmo.monitoring.loader.domain.loader.entity.LoadStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.service.execution.LoadExecutorService;
import com.tiqmo.monitoring.loader.service.locking.LoaderLock;
import com.tiqmo.monitoring.loader.service.locking.LockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
  private final LockManager lockManager;
  private final LoadExecutorService loadExecutorService;

  /**
   * Round 12: Auto-recovery threshold for FAILED loaders (20 minutes).
   */
  private static final Duration FAILED_RECOVERY_THRESHOLD = Duration.ofMinutes(20);

  /**
   * Main scheduling loop - runs every 10 seconds.
   *
   * <p>Coordinates Rounds 10-12:
   * <ul>
   *   <li>Round 12: Recover FAILED loaders</li>
   *   <li>Round 10: Basic scheduling logic</li>
   *   <li>Round 11: Priority-based execution</li>
   * </ul>
   */
  @Scheduled(fixedDelay = 10000, initialDelay = 5000)
  public void scheduleLoaders() {
    try {
      log.debug("Scheduler: Starting scheduling cycle");

      // Round 12: Auto-recover FAILED loaders
      recoverFailedLoaders();

      // Round 10: Find all enabled loaders
      List<Loader> enabledLoaders = loaderRepository.findAllByEnabledTrue();
      if (enabledLoaders.isEmpty()) {
        log.debug("Scheduler: No enabled loaders found");
        return;
      }

      log.debug("Scheduler: Found {} enabled loader(s)", enabledLoaders.size());

      // Round 11: Sort by priority (IDLE > RUNNING > FAILED)
      List<Loader> sortedLoaders = sortByPriority(enabledLoaders);

      // Round 10: Process each loader
      for (Loader loader : sortedLoaders) {
        try {
          processLoader(loader);
        } catch (Exception e) {
          log.error("Scheduler: Error processing loader {}: {}",
              loader.getLoaderCode(), e.getMessage(), e);
          // Continue with next loader
        }
      }

      log.debug("Scheduler: Scheduling cycle complete");

    } catch (Exception e) {
      log.error("Scheduler: Unexpected error in scheduling cycle", e);
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
      log.debug("Round 13: Starting stale lock cleanup");

      int cleaned = lockManager.cleanupStaleLocks();

      if (cleaned > 0) {
        log.warn("Round 13: Cleaned up {} stale lock(s) from crashed/terminated pods", cleaned);
      } else {
        log.debug("Round 13: No stale locks found");
      }

    } catch (Exception e) {
      log.error("Round 13: Error during stale lock cleanup", e);
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
    List<Loader> allLoaders = loaderRepository.findAll();
    Instant now = Instant.now();

    for (Loader loader : allLoaders) {
      if (loader.getLoadStatus() == LoadStatus.FAILED && loader.getFailedSince() != null) {
        Duration failedDuration = Duration.between(loader.getFailedSince(), now);

        if (failedDuration.compareTo(FAILED_RECOVERY_THRESHOLD) > 0) {
          log.info("Round 12: Auto-recovering FAILED loader {} (failed for {} minutes)",
              loader.getLoaderCode(), failedDuration.toMinutes());

          loader.setLoadStatus(LoadStatus.IDLE);
          loader.setFailedSince(null);
          loaderRepository.save(loader);

          log.info("Round 12: Loader {} reset to IDLE and ready for execution",
              loader.getLoaderCode());
        }
      }
    }
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
    try {
      log.info("Scheduler: Executing loader {} (status: {}, last run: {})",
          loaderCode, loader.getLoadStatus(), loader.getLastLoadTimestamp());

      // Execute the loader
      loadExecutorService.executeLoader(loader);

      log.info("Scheduler: Successfully executed loader {}", loaderCode);

    } catch (Exception e) {
      log.error("Scheduler: Failed to execute loader {}: {}", loaderCode, e.getMessage(), e);
    } finally {
      // Always release the lock
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
}
