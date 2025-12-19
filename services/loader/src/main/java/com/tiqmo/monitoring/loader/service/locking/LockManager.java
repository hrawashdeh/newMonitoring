package com.tiqmo.monitoring.loader.service.locking;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;

import java.util.Optional;

/**
 * Manages distributed execution locks for loader scheduling.
 *
 * <p>Implements short-lived lock pattern:
 * <ol>
 *   <li>Acquire lock (check limits, insert lock record)</li>
 *   <li>Release lock immediately (before data loading)</li>
 *   <li>Execute actual data loading (no locks held)</li>
 * </ol>
 *
 * <p><b>Concurrency Limits:</b>
 * <ul>
 *   <li>Per-loader: {@code loader.maxParallelExecutions} (default: 1)</li>
 *   <li>Global: 100 concurrent executions across all loaders</li>
 * </ul>
 *
 * <p><b>Stale Lock Cleanup:</b> Locks older than 2 hours are automatically
 * released (handles crashed pods that couldn't release locks).
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public interface LockManager {

  /**
   * Attempts to acquire execution lock for a loader.
   *
   * <p>Checks:
   * <ol>
   *   <li>Per-loader limit: Active locks &lt; {@code loader.maxParallelExecutions}</li>
   *   <li>Global limit: Total active locks &lt; 100</li>
   *   <li>If both pass: Creates lock record with unique lockId</li>
   * </ol>
   *
   * <p><b>Usage (Short-Lived Pattern):</b>
   * <pre>
   * Optional&lt;LoaderLock&gt; lock = lockManager.tryAcquireLock(loader);
   * if (lock.isPresent()) {
   *     try {
   *         // Create LoadHistory (RUNNING)
   *         lockManager.releaseLock(lock.get());  // ← Release immediately!
   *         // Now execute actual data loading (no locks held)
   *     } catch (Exception e) {
   *         lockManager.releaseLock(lock.get());  // Ensure release on error
   *     }
   * }
   * </pre>
   *
   * @param loader the loader to acquire lock for
   * @return lock if acquired, empty if limit reached
   * @throws IllegalArgumentException if loader is null
   */
  Optional<LoaderLock> tryAcquireLock(Loader loader);

  /**
   * Releases an execution lock.
   *
   * <p>Sets {@code released = true} and {@code releasedAt = now}.
   * Called immediately after creating LoadHistory record.
   *
   * @param lock the lock to release
   * @throws IllegalArgumentException if lock is null
   */
  void releaseLock(LoaderLock lock);

  /**
   * Releases a lock by lockId.
   *
   * <p>Convenience method when LoaderLock object is not available.
   *
   * @param lockId the lock ID to release
   */
  void releaseLock(String lockId);

  /**
   * Cleans up stale locks from crashed pods.
   *
   * <p>Releases locks that are:
   * <ul>
   *   <li>Not yet released ({@code released = false})</li>
   *   <li>Acquired more than 2 hours ago</li>
   * </ul>
   *
   * <p>Should be called periodically by scheduler service.
   *
   * @return number of stale locks released
   */
  int cleanupStaleLocks();

  /**
   * Counts active locks for a specific loader.
   *
   * @param loaderCode the loader code
   * @return number of active (unreleased) locks
   */
  long countActiveLocks(String loaderCode);

  /**
   * Counts total active locks across all loaders.
   *
   * @return total number of active locks
   */
  long countTotalActiveLocks();
}
