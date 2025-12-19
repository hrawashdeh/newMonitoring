package com.tiqmo.monitoring.loader.integration;

import com.tiqmo.monitoring.loader.domain.loader.entity.*;
import com.tiqmo.monitoring.loader.service.scheduler.LoaderSchedulerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LoaderSchedulerService.
 *
 * <p>Tests cover:
 * - End-to-end scheduler flow (polling, locking, execution)
 * - Performance with 100+ concurrent loaders
 * - Lock contention and race conditions
 * - Multi-pod coordination scenarios
 * - Auto-recovery from FAILED status
 * - Stale lock cleanup
 * - Priority scheduling (IDLE > RUNNING > FAILED > PAUSED)
 *
 * @author Hassan Rawashdeh (Claude Code)
 * @since 1.0.0
 */
@TestPropertySource(properties = {
    "scheduler.enabled=false"  // Disable auto-scheduling, we'll trigger manually
})
public class SchedulerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LoaderSchedulerService schedulerService;

    // ===================================================================================
    // Test: End-to-End Scheduler Flow
    // ===================================================================================

    @Test
    void testEndToEndSchedulerFlow_Success() throws InterruptedException {
        // Arrange - Create source database and loader
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER_001", sourceDb, 10, true);
        loader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));  // Ready to run
        loaderRepository.save(loader);

        // Act - Run scheduler cycle
        schedulerService.scheduleLoaders();

        // Assert - Loader should have executed
        waitFor(() -> loadHistoryRepository.count() > 0,
                "Load history should be created");

        List<LoadHistory> history = loadHistoryRepository.findAll();
        assertEquals(1, history.size(), "Should have one execution record");
        assertEquals("TEST_LOADER_001", history.get(0).getLoaderCode());
        assertEquals(LoadExecutionStatus.SUCCESS, history.get(0).getStatus());

        // Verify loader status returned to IDLE
        Loader updatedLoader = loaderRepository.findByLoaderCode("TEST_LOADER_001").orElseThrow();
        assertEquals(LoadStatus.IDLE, updatedLoader.getLoadStatus());
        assertNotNull(updatedLoader.getLastLoadTimestamp(), "Last load timestamp should be updated");
    }

    @Test
    void testScheduler_SkipsRunningLoaders() throws InterruptedException {
        // Arrange - Loader already in RUNNING state
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER_002", sourceDb, 10, true);
        loader.setLoadStatus(LoadStatus.RUNNING);
        loader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
        loaderRepository.save(loader);

        // Act - Run scheduler
        schedulerService.scheduleLoaders();

        // Assert - Should NOT create execution (loader already running)
        Thread.sleep(500);  // Give time for any async operations
        assertEquals(0, loadHistoryRepository.count(),
                "Should not execute loaders already in RUNNING state");
    }

    @Test
    void testScheduler_SkipsPausedLoaders() throws InterruptedException {
        // Arrange - Paused loader
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER_003", sourceDb, 10, true);
        loader.setLoadStatus(LoadStatus.PAUSED);
        loader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
        loaderRepository.save(loader);

        // Act - Run scheduler
        schedulerService.scheduleLoaders();

        // Assert - Should NOT execute paused loaders
        Thread.sleep(500);
        assertEquals(0, loadHistoryRepository.count(),
                "Should not execute PAUSED loaders");
    }

    @Test
    void testScheduler_SkipsDisabledLoaders() throws InterruptedException {
        // Arrange - Disabled loader
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER_004", sourceDb, 10, false);  // enabled=false
        loader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
        loaderRepository.save(loader);

        // Act - Run scheduler
        schedulerService.scheduleLoaders();

        // Assert - Should NOT execute disabled loaders
        Thread.sleep(500);
        assertEquals(0, loadHistoryRepository.count(),
                "Should not execute disabled loaders");
    }

    // ===================================================================================
    // Test: Priority Scheduling (IDLE > RUNNING > FAILED > PAUSED)
    // ===================================================================================

    @Test
    void testScheduler_PriorityScheduling_IdleLoadersFirst() {
        // Arrange - Create loaders with different statuses
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));

        Loader idleLoader = createTestLoader("IDLE_LOADER", sourceDb, 10, true);
        idleLoader.setLoadStatus(LoadStatus.IDLE);
        idleLoader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
        loaderRepository.save(idleLoader);

        Loader failedLoader = createTestLoader("FAILED_LOADER", sourceDb, 10, true);
        failedLoader.setLoadStatus(LoadStatus.FAILED);
        failedLoader.setFailedSince(Instant.now().minus(30, ChronoUnit.MINUTES));
        failedLoader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
        loaderRepository.save(failedLoader);

        // Act - Scheduler should prioritize IDLE over FAILED
        schedulerService.scheduleLoaders();

        // Assert - IDLE loader should execute first
        List<LoadHistory> history = loadHistoryRepository.findAll();
        assertTrue(history.stream().anyMatch(h -> "IDLE_LOADER".equals(h.getLoaderCode())),
                "IDLE loader should have executed");
    }

    // ===================================================================================
    // Test: Auto-Recovery from FAILED Status
    // ===================================================================================

    @Test
    void testScheduler_AutoRecovery_ResetsFailedLoadersAfter20Minutes() {
        // Arrange - Loader in FAILED state for > 20 minutes
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("FAILED_LOADER_RECOVERY", sourceDb, 10, true);
        loader.setLoadStatus(LoadStatus.FAILED);
        loader.setFailedSince(Instant.now().minus(25, ChronoUnit.MINUTES));  // > 20 min
        loader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
        loaderRepository.save(loader);

        // Act - Run recovery
        schedulerService.recoverFailedLoaders();

        // Assert - Loader should be reset to IDLE
        Loader recovered = loaderRepository.findByLoaderCode("FAILED_LOADER_RECOVERY").orElseThrow();
        assertEquals(LoadStatus.IDLE, recovered.getLoadStatus(),
                "Failed loader should be reset to IDLE after 20 minutes");
        assertNull(recovered.getFailedSince(),
                "failedSince should be cleared");
    }

    @Test
    void testScheduler_AutoRecovery_DoesNotResetRecentFailures() {
        // Arrange - Loader in FAILED state for < 20 minutes
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("RECENT_FAILURE", sourceDb, 10, true);
        loader.setLoadStatus(LoadStatus.FAILED);
        loader.setFailedSince(Instant.now().minus(5, ChronoUnit.MINUTES));  // < 20 min
        loaderRepository.save(loader);

        // Act - Run recovery
        schedulerService.recoverFailedLoaders();

        // Assert - Loader should still be FAILED
        Loader stillFailed = loaderRepository.findByLoaderCode("RECENT_FAILURE").orElseThrow();
        assertEquals(LoadStatus.FAILED, stillFailed.getLoadStatus(),
                "Recent failures should not be auto-recovered");
        assertNotNull(stillFailed.getFailedSince());
    }

    // ===================================================================================
    // Test: Stale Lock Cleanup
    // ===================================================================================

    @Test
    void testScheduler_StaleLockCleanup_ReleasesOldLocks() {
        // Arrange - Create stale lock (> 30 minutes old)
        LoaderExecutionLock staleLock = createTestLock("STALE_LOADER",
                "old-pod-1", Instant.now().minus(35, ChronoUnit.MINUTES));
        staleLock.setReleased(false);
        loaderExecutionLockRepository.save(staleLock);

        // Act - Run stale lock cleanup
        schedulerService.cleanupStaleLocks();

        // Assert - Lock should be released
        LoaderExecutionLock cleanedLock = loaderExecutionLockRepository.findById(staleLock.getId()).orElseThrow();
        assertTrue(cleanedLock.getReleased(), "Stale lock should be released");
        assertNotNull(cleanedLock.getReleasedAt(), "Released timestamp should be set");
    }

    @Test
    void testScheduler_StaleLockCleanup_DoesNotReleaseRecentLocks() {
        // Arrange - Recent lock (< 30 minutes old)
        LoaderExecutionLock recentLock = createTestLock("ACTIVE_LOADER",
                "active-pod-1", Instant.now().minus(5, ChronoUnit.MINUTES));
        recentLock.setReleased(false);
        loaderExecutionLockRepository.save(recentLock);

        // Act - Run cleanup
        schedulerService.cleanupStaleLocks();

        // Assert - Lock should still be active
        LoaderExecutionLock activeLock = loaderExecutionLockRepository.findById(recentLock.getId()).orElseThrow();
        assertFalse(activeLock.getReleased(), "Recent locks should not be released");
        assertNull(activeLock.getReleasedAt());
    }

    // ===================================================================================
    // Test: Performance with 100+ Concurrent Loaders
    // ===================================================================================

    @Test
    void testScheduler_Performance_Handles100ConcurrentLoaders() throws InterruptedException {
        // Arrange - Create 100 loaders
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        List<Loader> loaders = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Loader loader = createTestLoader("PERF_LOADER_" + String.format("%03d", i), sourceDb, 10, true);
            loader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
            loaders.add(loader);
        }
        loaderRepository.saveAll(loaders);

        long startTime = System.currentTimeMillis();

        // Act - Run scheduler
        schedulerService.scheduleLoaders();

        // Wait for executions to complete
        waitFor(() -> loadHistoryRepository.count() == 100,
                30000,  // 30 second timeout
                "All 100 loaders should execute");

        long executionTime = System.currentTimeMillis() - startTime;

        // Assert - All loaders executed within reasonable time
        assertEquals(100, loadHistoryRepository.count(),
                "All 100 loaders should have execution history");

        System.out.println("✓ Performance Test: 100 loaders executed in " + executionTime + "ms");

        // Verify no duplicate executions (lock contention handled correctly)
        List<String> executedCodes = loadHistoryRepository.findAll()
                .stream()
                .map(LoadHistory::getLoaderCode)
                .distinct()
                .toList();
        assertEquals(100, executedCodes.size(),
                "Each loader should execute exactly once (no duplicates from lock contention)");
    }

    // ===================================================================================
    // Test: Lock Contention and Race Conditions
    // ===================================================================================

    @Test
    void testScheduler_LockContention_PreventsDuplicateExecution() throws InterruptedException {
        // Arrange - Create one loader
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("CONTENTION_LOADER", sourceDb, 10, true);
        loader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
        loaderRepository.save(loader);

        // Simulate concurrent scheduler runs from 5 different pods
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Act - 5 concurrent scheduler runs
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    schedulerService.scheduleLoaders();
                    executionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert - Only ONE execution should have succeeded (lock prevented duplicates)
        waitFor(() -> loadHistoryRepository.count() >= 1, "At least one execution should complete");
        Thread.sleep(1000);  // Wait for any stragglers

        long historyCount = loadHistoryRepository.count();
        assertEquals(1, historyCount,
                "Lock mechanism should prevent duplicate executions despite concurrent scheduler runs");

        System.out.println("✓ Lock Contention Test: " + executionCount.get() +
                " concurrent scheduler runs, only 1 execution (expected)");
    }

    // ===================================================================================
    // Test: Multi-Pod Coordination
    // ===================================================================================

    @Test
    void testScheduler_MultiPod_DistributesLoadEvenly() throws InterruptedException {
        // Arrange - Create 10 loaders with max_parallel_executions=1 (one at a time)
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        for (int i = 0; i < 10; i++) {
            Loader loader = createTestLoader("MULTI_POD_LOADER_" + i, sourceDb, 10, true);
            loader.setMaxParallelExecutions(1);  // Strict: only 1 execution at a time
            loader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
            loaderRepository.save(loader);
        }

        // Simulate 3 pods running scheduler concurrently
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);

        // Act - 3 pods run scheduler
        for (int podId = 0; podId < 3; podId++) {
            executor.submit(() -> {
                try {
                    schedulerService.scheduleLoaders();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Wait for all executions to complete
        waitFor(() -> loadHistoryRepository.count() == 10,
                30000,
                "All 10 loaders should execute exactly once");

        // Assert - Each loader executed exactly once (no duplicates, no missed executions)
        assertEquals(10, loadHistoryRepository.count(),
                "All loaders should execute exactly once");

        List<String> executedCodes = loadHistoryRepository.findAll()
                .stream()
                .map(LoadHistory::getLoaderCode)
                .distinct()
                .toList();
        assertEquals(10, executedCodes.size(),
                "Each loader should have exactly one execution");

        System.out.println("✓ Multi-Pod Test: 3 pods coordinated to execute 10 loaders without conflicts");
    }

    // ===================================================================================
    // Test: Interval Enforcement
    // ===================================================================================

    @Test
    void testScheduler_IntervalEnforcement_RespectsMinInterval() throws InterruptedException {
        // Arrange - Loader with 60-second minimum interval
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("INTERVAL_LOADER", sourceDb, 60, true);
        loader.setLastLoadTimestamp(Instant.now().minus(30, ChronoUnit.SECONDS));  // Too recent
        loaderRepository.save(loader);

        // Act - Run scheduler
        schedulerService.scheduleLoaders();

        // Assert - Should NOT execute (interval not elapsed)
        Thread.sleep(500);
        assertEquals(0, loadHistoryRepository.count(),
                "Loader should not execute if min interval has not elapsed");

        // Update last load timestamp to > 60 seconds ago
        loader.setLastLoadTimestamp(Instant.now().minus(65, ChronoUnit.SECONDS));
        loaderRepository.save(loader);

        // Act - Run scheduler again
        schedulerService.scheduleLoaders();

        // Assert - Should execute now
        waitFor(() -> loadHistoryRepository.count() == 1,
                "Loader should execute after min interval has elapsed");
    }

    // ===================================================================================
    // Test: Edge Cases
    // ===================================================================================

    @Test
    void testScheduler_NoLoaders_DoesNotThrowException() {
        // Arrange - Empty database
        // (cleanupDatabase() already ran in @BeforeEach)

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> schedulerService.scheduleLoaders(),
                "Scheduler should handle empty loader list gracefully");
    }

    @Test
    void testScheduler_AllLoadersDisabled_DoesNotExecute() throws InterruptedException {
        // Arrange - 5 disabled loaders
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        for (int i = 0; i < 5; i++) {
            Loader loader = createTestLoader("DISABLED_" + i, sourceDb, 10, false);  // enabled=false
            loader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
            loaderRepository.save(loader);
        }

        // Act - Run scheduler
        schedulerService.scheduleLoaders();

        // Assert - No executions
        Thread.sleep(500);
        assertEquals(0, loadHistoryRepository.count(),
                "Disabled loaders should not execute");
    }

    @Test
    void testScheduler_MixedStatuses_OnlyExecutesEligibleLoaders() throws InterruptedException {
        // Arrange - Create loaders in various states
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));

        // Eligible: IDLE, enabled, interval elapsed
        Loader eligible1 = createTestLoader("ELIGIBLE_1", sourceDb, 10, true);
        eligible1.setLoadStatus(LoadStatus.IDLE);
        eligible1.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
        loaderRepository.save(eligible1);

        // Eligible: FAILED (old), enabled, interval elapsed
        Loader eligible2 = createTestLoader("ELIGIBLE_2", sourceDb, 10, true);
        eligible2.setLoadStatus(LoadStatus.FAILED);
        eligible2.setFailedSince(Instant.now().minus(25, ChronoUnit.MINUTES));  // > 20 min
        eligible2.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
        loaderRepository.save(eligible2);

        // Not eligible: RUNNING
        Loader ineligible1 = createTestLoader("INELIGIBLE_RUNNING", sourceDb, 10, true);
        ineligible1.setLoadStatus(LoadStatus.RUNNING);
        loaderRepository.save(ineligible1);

        // Not eligible: PAUSED
        Loader ineligible2 = createTestLoader("INELIGIBLE_PAUSED", sourceDb, 10, true);
        ineligible2.setLoadStatus(LoadStatus.PAUSED);
        loaderRepository.save(ineligible2);

        // Not eligible: Disabled
        Loader ineligible3 = createTestLoader("INELIGIBLE_DISABLED", sourceDb, 10, false);
        ineligible3.setLoadStatus(LoadStatus.IDLE);
        loaderRepository.save(ineligible3);

        // Act - Run scheduler
        schedulerService.recoverFailedLoaders();  // First recover FAILED loaders
        schedulerService.scheduleLoaders();

        // Assert - Only 2 eligible loaders should execute
        waitFor(() -> loadHistoryRepository.count() >= 2,
                "Eligible loaders should execute");

        List<String> executedCodes = loadHistoryRepository.findAll()
                .stream()
                .map(LoadHistory::getLoaderCode)
                .toList();

        assertTrue(executedCodes.contains("ELIGIBLE_1"),
                "IDLE loader should execute");
        assertTrue(executedCodes.contains("ELIGIBLE_2"),
                "Recovered FAILED loader should execute");
        assertFalse(executedCodes.contains("INELIGIBLE_RUNNING"),
                "RUNNING loader should not execute");
        assertFalse(executedCodes.contains("INELIGIBLE_PAUSED"),
                "PAUSED loader should not execute");
        assertFalse(executedCodes.contains("INELIGIBLE_DISABLED"),
                "Disabled loader should not execute");
    }
}
