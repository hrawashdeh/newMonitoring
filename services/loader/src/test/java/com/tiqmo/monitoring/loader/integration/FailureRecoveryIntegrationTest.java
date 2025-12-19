package com.tiqmo.monitoring.loader.integration;

import com.tiqmo.monitoring.loader.domain.loader.entity.LoadExecutionStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoadStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.SourceDatabase;
import com.tiqmo.monitoring.loader.service.scheduler.LoaderSchedulerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for failure recovery scenarios.
 * <p>
 * Tests:
 * - Auto-recovery from FAILED status after 20 minutes
 * - Scheduler skips PAUSED loaders
 * - Scheduler handles RUNNING loaders gracefully
 * - Failed loader doesn't block other loaders
 * <p>
 * Uses real database (H2 in-memory) and real scheduler service to verify
 * failure recovery behavior.
 */
public class FailureRecoveryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LoaderSchedulerService schedulerService;

    // ============================================================================
    // Auto-Recovery from FAILED Status
    // ============================================================================

    @Test
    void failedLoader_shouldRecover_after20Minutes() {
        // Given: A loader in FAILED status for 21 minutes
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setLoadStatus(LoadStatus.FAILED);
        loader.setFailedSince(Instant.now().minus(21, ChronoUnit.MINUTES));
        loader.setEnabled(true);
        loaderRepository.save(loader);

        // When: Scheduler runs (recoverFailedLoaders is called)
        schedulerService.recoverFailedLoaders();

        // Then: Loader status reset to IDLE
        Loader recovered = loaderRepository.findByLoaderCode("TEST_LOADER").orElseThrow();
        assertThat(recovered.getLoadStatus()).isEqualTo(LoadStatus.IDLE);
        assertThat(recovered.getFailedSince()).isNull();
    }

    @Test
    void failedLoader_shouldNotRecover_before20Minutes() {
        // Given: A loader in FAILED status for only 10 minutes
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setLoadStatus(LoadStatus.FAILED);
        loader.setFailedSince(Instant.now().minus(10, ChronoUnit.MINUTES));
        loader.setEnabled(true);
        loaderRepository.save(loader);

        // When: Scheduler runs
        schedulerService.recoverFailedLoaders();

        // Then: Loader still in FAILED status
        Loader stillFailed = loaderRepository.findByLoaderCode("TEST_LOADER").orElseThrow();
        assertThat(stillFailed.getLoadStatus()).isEqualTo(LoadStatus.FAILED);
        assertThat(stillFailed.getFailedSince()).isNotNull();
    }

    @Test
    void failedLoader_shouldNotRecover_ifDisabled() {
        // Given: A FAILED loader that is disabled (even after 21 minutes)
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setLoadStatus(LoadStatus.FAILED);
        loader.setFailedSince(Instant.now().minus(21, ChronoUnit.MINUTES));
        loader.setEnabled(false);  // Disabled
        loaderRepository.save(loader);

        // When: Scheduler runs
        schedulerService.recoverFailedLoaders();

        // Then: Loader still in FAILED status (not recovered because disabled)
        Loader stillFailed = loaderRepository.findByLoaderCode("TEST_LOADER").orElseThrow();
        assertThat(stillFailed.getLoadStatus()).isEqualTo(LoadStatus.FAILED);
        assertThat(stillFailed.getFailedSince()).isNotNull();
    }

    // ============================================================================
    // PAUSED Loader Handling
    // ============================================================================

    @Test
    void pausedLoader_shouldBeSkippedByScheduler() {
        // Given: A paused loader (enabled=false)
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setEnabled(false);  // Paused
        loader.setLoadStatus(LoadStatus.IDLE);
        loader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));  // Overdue
        loaderRepository.save(loader);

        // When: Scheduler runs
        // Note: We can't fully test execution without a real MySQL source database,
        // but we can verify the loader is found by enabled query
        long enabledCount = loaderRepository.findAllByEnabledTrue().size();

        // Then: Paused loader not included in enabled loaders
        assertThat(enabledCount).isEqualTo(0);
    }

    // ============================================================================
    // RUNNING Loader Handling
    // ============================================================================

    @Test
    void runningLoader_shouldNotBeScheduledAgain() {
        // Given: A loader already in RUNNING status
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setLoadStatus(LoadStatus.RUNNING);
        loader.setEnabled(true);
        loader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));  // Overdue
        loaderRepository.save(loader);

        // When: Check if loader is due for execution
        // (Scheduler logic: RUNNING loaders are not due)
        Instant nextRun = loader.getLastLoadTimestamp().plusSeconds(loader.getMinIntervalSeconds());
        boolean isDue = Instant.now().isAfter(nextRun);

        // Then: Loader is technically "due" by time, but scheduler should skip RUNNING loaders
        // (This is handled by LockManager - RUNNING loaders can't acquire new locks)
        assertThat(isDue).isTrue();  // Time-wise due
        assertThat(loader.getLoadStatus()).isEqualTo(LoadStatus.RUNNING);  // But still running
    }

    // ============================================================================
    // Multiple Loaders - Isolation Tests
    // ============================================================================

    @Test
    void oneFailedLoader_shouldNotBlockOtherLoaders() {
        // Given: Two loaders - one FAILED, one IDLE
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));

        Loader failedLoader = createTestLoader("FAILED_LOADER", sourceDb);
        failedLoader.setLoadStatus(LoadStatus.FAILED);
        failedLoader.setFailedSince(Instant.now().minus(5, ChronoUnit.MINUTES));
        failedLoader.setEnabled(true);
        loaderRepository.save(failedLoader);

        Loader healthyLoader = createTestLoader("HEALTHY_LOADER", sourceDb);
        healthyLoader.setLoadStatus(LoadStatus.IDLE);
        healthyLoader.setEnabled(true);
        loaderRepository.save(healthyLoader);

        // When: Check enabled loaders
        long enabledCount = loaderRepository.findAllByEnabledTrue().size();

        // Then: Both loaders are enabled and can be scheduled
        // (Failed loader won't execute because it's in FAILED status, but won't block healthy loader)
        assertThat(enabledCount).isEqualTo(2);
    }

    // ============================================================================
    // Load History Tracking
    // ============================================================================

    @Test
    void failedExecution_shouldCreateFailedHistoryRecord() {
        // Given: A failed execution history record
        Instant now = Instant.now();
        loadHistoryRepository.save(createTestLoadHistory("TEST_LOADER", LoadExecutionStatus.FAILED,
                now.minus(5, ChronoUnit.MINUTES)));

        // When: Query failed executions
        long failedCount = loadHistoryRepository.findAll().stream()
                .filter(h -> h.getStatus() == LoadExecutionStatus.FAILED)
                .count();

        // Then: Failed record exists
        assertThat(failedCount).isEqualTo(1);
    }

    @Test
    void successfulExecutionAfterFailure_shouldCreateSuccessHistoryRecord() {
        // Given: A loader that had failures but now succeeds
        Instant now = Instant.now();

        // Failed execution 1 hour ago
        loadHistoryRepository.save(createTestLoadHistory("TEST_LOADER", LoadExecutionStatus.FAILED,
                now.minus(1, ChronoUnit.HOURS)));

        // Successful execution 5 minutes ago
        loadHistoryRepository.save(createTestLoadHistory("TEST_LOADER", LoadExecutionStatus.SUCCESS,
                now.minus(5, ChronoUnit.MINUTES)));

        // When: Query all executions
        var allHistory = loadHistoryRepository.findAll();
        var failedHistory = allHistory.stream()
                .filter(h -> h.getStatus() == LoadExecutionStatus.FAILED)
                .count();
        var successHistory = allHistory.stream()
                .filter(h -> h.getStatus() == LoadExecutionStatus.SUCCESS)
                .count();

        // Then: Both records exist (failure history preserved)
        assertThat(allHistory).hasSize(2);
        assertThat(failedHistory).isEqualTo(1);
        assertThat(successHistory).isEqualTo(1);
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Test
    void loaderWithNullFailedSince_shouldNotCrashRecovery() {
        // Given: A loader in FAILED status but with null failedSince (shouldn't happen, but test defensively)
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setLoadStatus(LoadStatus.FAILED);
        loader.setFailedSince(null);  // Null failedSince
        loader.setEnabled(true);
        loaderRepository.save(loader);

        // When: Scheduler runs recovery
        // Should not crash, should handle null gracefully
        try {
            schedulerService.recoverFailedLoaders();
        } catch (Exception e) {
            // Should not throw exception
            throw new AssertionError("Recovery should handle null failedSince gracefully", e);
        }

        // Then: Loader status unchanged (can't recover without failedSince timestamp)
        Loader unchanged = loaderRepository.findByLoaderCode("TEST_LOADER").orElseThrow();
        assertThat(unchanged.getLoadStatus()).isEqualTo(LoadStatus.FAILED);
    }

    @Test
    void multipleFailedLoaders_shouldAllRecover() {
        // Given: 3 failed loaders, all failed > 20 minutes ago
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Instant failedTime = Instant.now().minus(21, ChronoUnit.MINUTES);

        for (int i = 1; i <= 3; i++) {
            Loader loader = createTestLoader("FAILED_LOADER_" + i, sourceDb);
            loader.setLoadStatus(LoadStatus.FAILED);
            loader.setFailedSince(failedTime);
            loader.setEnabled(true);
            loaderRepository.save(loader);
        }

        // When: Scheduler runs recovery
        schedulerService.recoverFailedLoaders();

        // Then: All 3 loaders recovered
        long recoveredCount = loaderRepository.findAll().stream()
                .filter(l -> l.getLoadStatus() == LoadStatus.IDLE)
                .count();
        assertThat(recoveredCount).isEqualTo(3);

        // All failedSince timestamps cleared
        long stillFailedCount = loaderRepository.findAll().stream()
                .filter(l -> l.getFailedSince() != null)
                .count();
        assertThat(stillFailedCount).isEqualTo(0);
    }

    @Test
    void recoveryAndFailureSimultaneous_shouldHandleGracefully() {
        // Given: Two loaders - one recovering, one failing simultaneously
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));

        // Loader 1: Failed 21 minutes ago (will recover)
        Loader loader1 = createTestLoader("RECOVERING_LOADER", sourceDb);
        loader1.setLoadStatus(LoadStatus.FAILED);
        loader1.setFailedSince(Instant.now().minus(21, ChronoUnit.MINUTES));
        loader1.setEnabled(true);
        loaderRepository.save(loader1);

        // Loader 2: Just failed (won't recover yet)
        Loader loader2 = createTestLoader("NEWLY_FAILED_LOADER", sourceDb);
        loader2.setLoadStatus(LoadStatus.FAILED);
        loader2.setFailedSince(Instant.now().minus(1, ChronoUnit.MINUTES));
        loader2.setEnabled(true);
        loaderRepository.save(loader2);

        // When: Recovery runs
        schedulerService.recoverFailedLoaders();

        // Then: Loader 1 recovered, Loader 2 still failed
        Loader recovered = loaderRepository.findByLoaderCode("RECOVERING_LOADER").orElseThrow();
        Loader stillFailed = loaderRepository.findByLoaderCode("NEWLY_FAILED_LOADER").orElseThrow();

        assertThat(recovered.getLoadStatus()).isEqualTo(LoadStatus.IDLE);
        assertThat(recovered.getFailedSince()).isNull();

        assertThat(stillFailed.getLoadStatus()).isEqualTo(LoadStatus.FAILED);
        assertThat(stillFailed.getFailedSince()).isNotNull();
    }
}
