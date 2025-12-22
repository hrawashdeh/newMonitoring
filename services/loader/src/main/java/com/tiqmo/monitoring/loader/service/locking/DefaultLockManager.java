package com.tiqmo.monitoring.loader.service.locking;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderExecutionLock;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderExecutionLockRepository;
import com.tiqmo.monitoring.loader.infra.ReplicaNameProvider;
import com.tiqmo.monitoring.loader.infra.config.LockingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Default implementation of LockManager.
 *
 * <p>Uses database-backed locking with {@link LoaderExecutionLock} entity.
 * Enforces per-loader and global concurrency limits.
 *
 * <p>Tracks active execution threads for timeout and cancellation support.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultLockManager implements LockManager {

  private static final int GLOBAL_LIMIT = 100;

  private final LoaderExecutionLockRepository lockRepository;
  private final ReplicaNameProvider replicaNameProvider;
  private final LockingProperties lockingProperties;

  /**
   * Thread registry: Maps lockId to Future for active executions.
   * Allows cancellation of hung threads when stale locks are cleaned up.
   */
  private final Map<String, Future<?>> activeExecutions = new ConcurrentHashMap<>();

  @Override
  @Transactional
  public Optional<LoaderLock> tryAcquireLock(Loader loader) {
    if (loader == null) {
      throw new IllegalArgumentException("Loader cannot be null");
    }

    String loaderCode = loader.getLoaderCode();
    int maxParallel = loader.getMaxParallelExecutions();

    log.debug("Attempting to acquire lock for loader: {} (max parallel: {})",
        loaderCode, maxParallel);

    // 1. Check per-loader limit
    long activeLocks = lockRepository.countByLoaderCodeAndReleased(loaderCode, false);
    if (activeLocks >= maxParallel) {
      log.debug("Loader {} has reached max parallel executions ({}/{})",
          loaderCode, activeLocks, maxParallel);
      return Optional.empty();
    }

    // 2. Check global limit
    long totalActiveLocks = lockRepository.countByReleased(false);
    if (totalActiveLocks >= GLOBAL_LIMIT) {
      log.debug("Global concurrency limit reached ({}/{})",
          totalActiveLocks, GLOBAL_LIMIT);
      return Optional.empty();
    }

    // 3. Acquire lock
    String lockId = UUID.randomUUID().toString();
    String replicaName = replicaNameProvider.getReplicaName();
    Instant acquiredAt = Instant.now();

    LoaderExecutionLock lockEntity = LoaderExecutionLock.builder()
        .loaderCode(loaderCode)
        .lockId(lockId)
        .replicaName(replicaName)
        .acquiredAt(acquiredAt)
        .released(false)
        .build();

    lockRepository.save(lockEntity);

    log.info("Lock acquired for loader: {} by replica: {} (lockId: {})",
        loaderCode, replicaName, lockId);

    return Optional.of(LoaderLock.builder()
        .lockId(lockId)
        .loaderCode(loaderCode)
        .replicaName(replicaName)
        .acquiredAt(acquiredAt)
        .build());
  }

  @Override
  @Transactional
  public void releaseLock(LoaderLock lock) {
    if (lock == null) {
      throw new IllegalArgumentException("Lock cannot be null");
    }
    releaseLock(lock.getLockId());
  }

  @Override
  @Transactional
  public void releaseLock(String lockId) {
    if (lockId == null || lockId.isBlank()) {
      throw new IllegalArgumentException("Lock ID cannot be null or blank");
    }

    Instant releasedAt = Instant.now();
    int updated = lockRepository.releaseLock(lockId, releasedAt);

    if (updated > 0) {
      log.debug("Lock released: {}", lockId);
    } else {
      log.warn("Failed to release lock (not found or already released): {}", lockId);
    }
  }

  @Override
  @Transactional
  public int cleanupStaleLocks() {
    int thresholdHours = lockingProperties.getStaleLockThresholdHours();
    Instant staleThreshold = Instant.now().minusSeconds(thresholdHours * 3600);
    Instant releasedAt = Instant.now();

    // Find stale locks before releasing them (to get lockIds for thread cancellation)
    List<LoaderExecutionLock> staleLocks = lockRepository.findByReleasedAndAcquiredAtBefore(
        false, staleThreshold);

    if (staleLocks.isEmpty()) {
      return 0;
    }

    // Cancel associated threads BEFORE releasing locks
    int cancelledThreads = 0;
    for (LoaderExecutionLock lock : staleLocks) {
      String lockId = lock.getLockId();
      Future<?> future = activeExecutions.get(lockId);

      if (future != null && !future.isDone()) {
        log.warn("Cancelling hung execution thread for stale lock: {} (loader: {}, acquired: {})",
            lockId, lock.getLoaderCode(), lock.getAcquiredAt());

        boolean cancelled = future.cancel(true); // Interrupt the thread
        if (cancelled) {
          cancelledThreads++;
          log.warn("Successfully cancelled thread for lock: {}", lockId);
        } else {
          log.error("Failed to cancel thread for lock: {} (may have already completed)", lockId);
        }

        activeExecutions.remove(lockId);
      }
    }

    // Now release the stale locks in database
    int cleaned = lockRepository.cleanupStaleLocks(staleThreshold, releasedAt);

    if (cleaned > 0) {
      log.warn("Cleaned up {} stale lock(s) (older than {} hours) and cancelled {} hung thread(s)",
          cleaned, thresholdHours, cancelledThreads);
    }

    return cleaned;
  }

  @Override
  public void registerExecution(String lockId, Future<?> future) {
    if (lockId == null || lockId.isBlank()) {
      throw new IllegalArgumentException("Lock ID cannot be null or blank");
    }
    if (future == null) {
      throw new IllegalArgumentException("Future cannot be null");
    }

    activeExecutions.put(lockId, future);
    log.debug("Registered execution thread for lock: {}", lockId);
  }

  @Override
  public void unregisterExecution(String lockId) {
    if (lockId == null || lockId.isBlank()) {
      return;
    }

    Future<?> removed = activeExecutions.remove(lockId);
    if (removed != null) {
      log.debug("Unregistered execution thread for lock: {}", lockId);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public long countActiveLocks(String loaderCode) {
    return lockRepository.countByLoaderCodeAndReleased(loaderCode, false);
  }

  @Override
  @Transactional(readOnly = true)
  public long countTotalActiveLocks() {
    return lockRepository.countByReleased(false);
  }
}
