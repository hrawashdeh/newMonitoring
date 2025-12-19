package com.tiqmo.monitoring.loader.integration;

import com.tiqmo.monitoring.loader.domain.loader.entity.*;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoadHistoryRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderExecutionLockRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.SourceDatabaseRepository;
import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import com.tiqmo.monitoring.loader.domain.signals.repo.SignalsHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Base class for integration tests.
 * <p>
 * Provides common setup, utilities, and test data builders for integration tests.
 * Uses H2 in-memory database for fast test execution.
 * <p>
 * Extending classes get:
 * - Spring Boot application context
 * - In-memory H2 database (PostgreSQL compatibility mode)
 * - Repositories autowired
 * - Test data builders
 * - Automatic database cleanup between tests
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected LoaderRepository loaderRepository;

    @Autowired
    protected SourceDatabaseRepository sourceDatabaseRepository;

    @Autowired
    protected LoadHistoryRepository loadHistoryRepository;

    @Autowired
    protected LoaderExecutionLockRepository loaderExecutionLockRepository;

    @Autowired
    protected SignalsHistoryRepository signalsHistoryRepository;

    /**
     * Clean up database before each test.
     * Ensures tests start with a clean slate.
     */
    @BeforeEach
    public void cleanupDatabase() {
        signalsHistoryRepository.deleteAll();
        loadHistoryRepository.deleteAll();
        loaderExecutionLockRepository.deleteAll();
        loaderRepository.deleteAll();
        sourceDatabaseRepository.deleteAll();
    }

    // ============================================================================
    // Test Data Builders
    // ============================================================================

    /**
     * Create a test SourceDatabase entity.
     *
     * @param code Database code
     * @return SourceDatabase entity (not saved)
     */
    protected SourceDatabase createTestSourceDatabase(String code) {
        return SourceDatabase.builder()
                .dbCode(code)
                .ip("localhost")
                .port(3306)
                .dbName("test_db")
                .userName("test_user")
                .passWord("test_password")  // Will be encrypted by JPA converter
                .dbType(SourceDatabase.DbType.MYSQL)
                .build();
    }

    /**
     * Create a test Loader entity.
     *
     * @param loaderCode       Loader code
     * @param sourceDb         Source database
     * @param minIntervalSecs  Minimum interval seconds
     * @param enabled          Whether loader is enabled
     * @return Loader entity (not saved)
     */
    protected Loader createTestLoader(String loaderCode, SourceDatabase sourceDb,
                                      int minIntervalSecs, boolean enabled) {
        return Loader.builder()
                .loaderCode(loaderCode)
                .sourceDatabase(sourceDb)
                .loaderSql("SELECT segment1, segment2, segment3, segment4, segment5, " +
                           "segment6, segment7, segment8, segment9, segment10, " +
                           "signal_value, signal_min, signal_max, signal_count, signal_avg, " +
                           "created_at FROM test_table WHERE created_at BETWEEN :fromTime AND :toTime")
                .minIntervalSeconds(minIntervalSecs)
                .maxIntervalSeconds(minIntervalSecs * 2)
                .maxQueryPeriodSeconds(3600)  // 1 hour
                .maxParallelExecutions(1)
                .loadStatus(LoadStatus.IDLE)
                .purgeStrategy(PurgeStrategy.FAIL_ON_DUPLICATE)
                .enabled(enabled)
                .build();
    }

    /**
     * Create a test Loader entity with default values (enabled, 10s interval).
     *
     * @param loaderCode Loader code
     * @param sourceDb   Source database
     * @return Loader entity (not saved)
     */
    protected Loader createTestLoader(String loaderCode, SourceDatabase sourceDb) {
        return createTestLoader(loaderCode, sourceDb, 10, true);
    }

    /**
     * Create a test LoadHistory entity.
     *
     * @param loaderCode Loader code
     * @param status     Execution status
     * @param startTime  Start time
     * @return LoadHistory entity (not saved)
     */
    protected LoadHistory createTestLoadHistory(String loaderCode, LoadExecutionStatus status,
                                                Instant startTime) {
        Instant endTime = startTime.plus(30, ChronoUnit.SECONDS);
        return LoadHistory.builder()
                .loaderCode(loaderCode)
                .sourceDatabaseCode("TEST_DB")
                .startTime(startTime)
                .endTime(endTime)
                .durationSeconds(30L)
                .queryFromTime(startTime.minus(1, ChronoUnit.HOURS))
                .queryToTime(startTime)
                .status(status)
                .recordsLoaded(status == LoadExecutionStatus.SUCCESS ? 100L : 0L)
                .recordsIngested(status == LoadExecutionStatus.SUCCESS ? 100L : 0L)
                .errorMessage(status == LoadExecutionStatus.FAILED ? "Test error" : null)
                .replicaName("test-pod-1")
                .build();
    }

    /**
     * Create a test LoaderExecutionLock entity.
     *
     * @param loaderCode Loader code
     * @param replicaName Replica name
     * @param acquiredAt Acquired timestamp
     * @return LoaderExecutionLock entity (not saved)
     */
    protected LoaderExecutionLock createTestLock(String loaderCode, String replicaName,
                                                 Instant acquiredAt) {
        return LoaderExecutionLock.builder()
                .loaderCode(loaderCode)
                .replicaName(replicaName)
                .acquiredAt(acquiredAt)
                .build();
    }

    /**
     * Create a test SignalsHistory entity.
     *
     * @param loaderCode Loader code
     * @param timestamp  Timestamp
     * @return SignalsHistory entity (not saved)
     */
    protected SignalsHistory createTestSignalsHistory(String loaderCode, Instant timestamp) {
        return SignalsHistory.builder()
                .loaderCode(loaderCode)
                .loadTimeStamp(timestamp.getEpochSecond())
                .segmentCode("SEG1")
                .recCount(5L)
                .maxVal(110.0)
                .minVal(90.0)
                .avgVal(100.0)
                .sumVal(500.0)
                .createTime(timestamp.getEpochSecond())
                .build();
    }

    // ============================================================================
    // Assertion Helpers
    // ============================================================================

    /**
     * Wait for a condition to be true (for async operations in tests).
     *
     * @param condition Condition to check
     * @param timeoutMs Timeout in milliseconds
     * @param message   Failure message
     * @throws InterruptedException If interrupted while waiting
     */
    protected void waitFor(java.util.function.Supplier<Boolean> condition, long timeoutMs,
                           String message) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (!condition.get()) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                throw new AssertionError("Timeout waiting for: " + message);
            }
            Thread.sleep(100);  // Poll every 100ms
        }
    }

    /**
     * Wait for a condition to be true with default 5-second timeout.
     *
     * @param condition Condition to check
     * @param message   Failure message
     * @throws InterruptedException If interrupted while waiting
     */
    protected void waitFor(java.util.function.Supplier<Boolean> condition, String message)
            throws InterruptedException {
        waitFor(condition, 5000, message);
    }
}
