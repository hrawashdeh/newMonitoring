package com.tiqmo.monitoring.loader.service.locking;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderExecutionLock;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderExecutionLockRepository;
import com.tiqmo.monitoring.loader.infra.ReplicaNameProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of LockManager.
 *
 * <p>Uses database-backed locking with {@link LoaderExecutionLock} entity.
 * Enforces per-loader and global concurrency limits.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultLockManager implements LockManager {

  private static final int GLOBAL_LIMIT = 100;
  private static final long STALE_LOCK_THRESHOLD_HOURS = 2;

  private final LoaderExecutionLockRepository lockRepository;
  private final ReplicaNameProvider replicaNameProvider;

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
    Instant staleThreshold = Instant.now().minusSeconds(STALE_LOCK_THRESHOLD_HOURS * 3600);
    Instant releasedAt = Instant.now();

    int cleaned = lockRepository.cleanupStaleLocks(staleThreshold, releasedAt);

    if (cleaned > 0) {
      log.warn("Cleaned up {} stale locks (older than {} hours)",
          cleaned, STALE_LOCK_THRESHOLD_HOURS);
    }

    return cleaned;
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
