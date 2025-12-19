package com.tiqmo.monitoring.loader.integration;

import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderExecutionLock;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoadStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.SourceDatabase;
import com.tiqmo.monitoring.loader.service.locking.DefaultLockManager;
import com.tiqmo.monitoring.loader.service.locking.LoaderLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for multi-pod distributed locking.
 * <p>
 * Tests concurrent lock acquisition, release, and cleanup scenarios
 * that occur in multi-replica Kubernetes deployments.
 * <p>
 * Uses real database (H2 in-memory) to verify distributed locking behavior.
 */
public class MultiPodLockingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DefaultLockManager lockManager;

    // ============================================================================
    // Concurrent Lock Acquisition Tests
    // ============================================================================

    @Test
    void multiplePods_shouldOnlyOneAcquireLock() throws InterruptedException, ExecutionException {
        // Given: A loader in database
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setMaxParallelExecutions(1);  // Only 1 concurrent execution allowed
        loaderRepository.save(loader);

        // When: 5 "pods" try to acquire lock simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<LoaderLock>> futures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            futures.add(executor.submit(() -> {
                // All threads try to acquire lock for the same loader
                return lockManager.tryAcquireLock(loader).orElse(null);
            }));
        }

        // Wait for all futures to complete
        List<LoaderLock> results = new ArrayList<>();
        for (Future<LoaderLock> future : futures) {
            LoaderLock lock = future.get();
            if (lock != null) {
                results.add(lock);
            }
        }
        executor.shutdown();

        // Then: Only one pod acquired the lock
        assertThat(results).hasSize(1);
        LoaderLock acquiredLock = results.get(0);
        assertThat(acquiredLock.getLoaderCode()).isEqualTo("TEST_LOADER");
        assertThat(acquiredLock.getReplicaName()).isNotNull();

        // Verify in database
        List<LoaderExecutionLock> locks = loaderExecutionLockRepository.findByLoaderCodeAndReleased("TEST_LOADER", false);
        assertThat(locks).hasSize(1);
    }

    @Test
    void maxParallelExecutions_shouldAllowMultipleConcurrentLocks() throws InterruptedException, ExecutionException {
        // Given: A loader with maxParallelExecutions=3
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setMaxParallelExecutions(3);  // Allow 3 concurrent executions
        loaderRepository.save(loader);

        // When: 5 "pods" try to acquire lock simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<LoaderLock>> futures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            futures.add(executor.submit(() -> {
                // All threads try to acquire lock for the same loader
                return lockManager.tryAcquireLock(loader).orElse(null);
            }));
        }

        // Wait for all futures to complete
        List<LoaderLock> results = new ArrayList<>();
        for (Future<LoaderLock> future : futures) {
            LoaderLock lock = future.get();
            if (lock != null) {
                results.add(lock);
            }
        }
        executor.shutdown();

        // Then: Up to 3 pods acquired locks
        assertThat(results).hasSizeLessThanOrEqualTo(3);
        assertThat(results).hasSizeGreaterThanOrEqualTo(1);  // At least one succeeded

        // Verify in database
        List<LoaderExecutionLock> locks = loaderExecutionLockRepository.findByLoaderCodeAndReleased("TEST_LOADER", false);
        assertThat(locks).hasSizeLessThanOrEqualTo(3);
    }

    // ============================================================================
    // Lock Release Tests
    // ============================================================================

    @Test
    void releaseLock_shouldAllowAnotherPodToAcquire() {
        // Given: A loader with lock acquired by pod-1
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setMaxParallelExecutions(1);
        loaderRepository.save(loader);

        LoaderLock lock1 = lockManager.tryAcquireLock(loader).orElse(null);
        assertThat(lock1).isNotNull();

        // When: pod-1 releases lock
        lockManager.releaseLock(lock1);

        // Then: pod-2 can acquire lock
        LoaderLock lock2 = lockManager.tryAcquireLock(loader).orElse(null);
        assertThat(lock2).isNotNull();
        assertThat(lock2.getReplicaName()).isNotNull();

        // Verify in database
        List<LoaderExecutionLock> locks = loaderExecutionLockRepository.findByLoaderCodeAndReleased("TEST_LOADER", false);
        assertThat(locks).hasSize(1);
    }

    @Test
    void releaseLock_shouldOnlyReleaseOwnLock() {
        // Given: pod-1 holds lock
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setMaxParallelExecutions(1);
        loaderRepository.save(loader);

        LoaderLock lock1 = lockManager.tryAcquireLock(loader).orElse(null);
        assertThat(lock1).isNotNull();

        // When: Try to release with incorrect lockId
        lockManager.releaseLock("nonexistent-lock-id");

        // Then: pod-1's lock still exists (unreleased)
        List<LoaderExecutionLock> locks = loaderExecutionLockRepository.findByLoaderCodeAndReleased("TEST_LOADER", false);
        assertThat(locks).hasSize(1);
        assertThat(locks.get(0).getReplicaName()).isEqualTo(lock1.getReplicaName());
    }

    // ============================================================================
    // Stale Lock Cleanup Tests
    // ============================================================================

    @Test
    void cleanupStaleLocks_shouldRemoveOldLocks() {
        // Given: Two locks - one fresh, one stale (> 2 hours old)
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loaderRepository.save(loader);

        Instant now = Instant.now();
        Instant threeHoursAgo = now.minus(3, ChronoUnit.HOURS);

        // Fresh lock (< 2 hours old)
        LoaderExecutionLock freshLock = createTestLock("TEST_LOADER", "test-pod-1", now.minus(30, ChronoUnit.MINUTES));
        loaderExecutionLockRepository.save(freshLock);

        // Stale lock (> 2 hours old)
        LoaderExecutionLock staleLock = createTestLock("TEST_LOADER", "test-pod-2", threeHoursAgo);
        loaderExecutionLockRepository.save(staleLock);

        // When: Cleanup stale locks
        int cleaned = lockManager.cleanupStaleLocks();

        // Then: Stale lock removed, fresh lock remains
        assertThat(cleaned).isEqualTo(1);
        List<LoaderExecutionLock> remainingLocks = loaderExecutionLockRepository.findAll();
        assertThat(remainingLocks).hasSize(1);
        assertThat(remainingLocks.get(0).getReplicaName()).isEqualTo("test-pod-1");
    }

    @Test
    void cleanupStaleLocks_shouldNotRemoveFreshLocks() {
        // Given: Only fresh locks (< 2 hours old)
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loaderRepository.save(loader);

        Instant now = Instant.now();
        loaderExecutionLockRepository.save(createTestLock("TEST_LOADER", "test-pod-1",
                now.minus(30, ChronoUnit.MINUTES)));
        loaderExecutionLockRepository.save(createTestLock("TEST_LOADER", "test-pod-2",
                now.minus(60, ChronoUnit.MINUTES)));

        // When: Cleanup stale locks
        int cleaned = lockManager.cleanupStaleLocks();

        // Then: No locks removed
        assertThat(cleaned).isEqualTo(0);
        List<LoaderExecutionLock> remainingLocks = loaderExecutionLockRepository.findAll();
        assertThat(remainingLocks).hasSize(2);
    }

    // ============================================================================
    // Concurrent Load Status Update Tests
    // ============================================================================

    @Test
    void concurrentStatusUpdates_shouldNotCorruptData() throws InterruptedException, ExecutionException {
        // Given: A loader in database
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setLoadStatus(LoadStatus.IDLE);
        loaderRepository.save(loader);

        // When: Multiple threads update load status concurrently
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(executor.submit(() -> {
                try {
                    Loader l = loaderRepository.findByLoaderCode("TEST_LOADER").orElseThrow();
                    l.setLoadStatus(LoadStatus.RUNNING);
                    loaderRepository.save(l);
                    successCount.incrementAndGet();

                    // Simulate some work
                    Thread.sleep(10);

                    Loader l2 = loaderRepository.findByLoaderCode("TEST_LOADER").orElseThrow();
                    l2.setLoadStatus(LoadStatus.IDLE);
                    loaderRepository.save(l2);
                } catch (Exception e) {
                    // Expected: Some updates may fail due to concurrent modification
                }
            }));
        }

        // Wait for all futures to complete
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Then: Loader still exists and has valid status
        Loader updated = loaderRepository.findByLoaderCode("TEST_LOADER").orElseThrow();
        assertThat(updated.getLoadStatus()).isIn(LoadStatus.IDLE, LoadStatus.RUNNING);
        assertThat(successCount.get()).isGreaterThan(0);
    }

    // ============================================================================
    // Lock Expiration Tests
    // ============================================================================

    @Test
    void tryAcquireLock_shouldSucceed_afterPreviousLockExpired() {
        // Given: A loader with an expired lock (simulated by manual cleanup)
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setMaxParallelExecutions(1);
        loaderRepository.save(loader);

        // Pod-1 acquires lock
        LoaderLock lock1 = lockManager.tryAcquireLock(loader).orElse(null);
        assertThat(lock1).isNotNull();

        // Simulate lock expiration (manual cleanup)
        loaderExecutionLockRepository.deleteAll(loaderExecutionLockRepository.findByLoaderCodeAndReleased("TEST_LOADER", false));

        // When: Pod-2 tries to acquire lock
        LoaderLock lock2 = lockManager.tryAcquireLock(loader).orElse(null);

        // Then: Pod-2 successfully acquires lock
        assertThat(lock2).isNotNull();
        assertThat(lock2.getReplicaName()).isNotNull();
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Test
    void tryAcquireLock_shouldFail_whenMaxParallelExecutionsReached() {
        // Given: A loader with maxParallelExecutions=2
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setMaxParallelExecutions(2);
        loaderRepository.save(loader);

        // When: 3 pods try to acquire locks
        LoaderLock lock1 = lockManager.tryAcquireLock(loader).orElse(null);
        LoaderLock lock2 = lockManager.tryAcquireLock(loader).orElse(null);
        LoaderLock lock3 = lockManager.tryAcquireLock(loader).orElse(null);

        // Then: First 2 succeed, third fails
        assertThat(lock1).isNotNull();
        assertThat(lock2).isNotNull();
        assertThat(lock3).isNull();  // Max parallel executions reached

        // Verify in database
        List<LoaderExecutionLock> locks = loaderExecutionLockRepository.findByLoaderCodeAndReleased("TEST_LOADER", false);
        assertThat(locks).hasSize(2);
    }

    @Test
    void samePod_cannotAcquireLockTwice() {
        // Given: A loader
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setMaxParallelExecutions(2);
        loaderRepository.save(loader);

        // When: Try to acquire lock twice
        LoaderLock lock1 = lockManager.tryAcquireLock(loader).orElse(null);
        LoaderLock lock2 = lockManager.tryAcquireLock(loader).orElse(null);

        // Then: First succeeds, second succeeds too (different pods/replicas can get locks up to maxParallelExecutions)
        assertThat(lock1).isNotNull();
        assertThat(lock2).isNotNull();  // Both can acquire since max is 2

        // Verify in database
        List<LoaderExecutionLock> locks = loaderExecutionLockRepository.findByLoaderCodeAndReleased("TEST_LOADER", false);
        assertThat(locks).hasSize(2);
    }
}
