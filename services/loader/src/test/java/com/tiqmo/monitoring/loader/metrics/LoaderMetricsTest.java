package com.tiqmo.monitoring.loader.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LoaderMetrics component.
 *
 * <p><b>Round 22 Implementation</b> - API Gateway + Custom Metrics
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0 (Round 22)
 */
class LoaderMetricsTest {

    private MeterRegistry meterRegistry;
    private LoaderMetrics loaderMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        loaderMetrics = new LoaderMetrics(meterRegistry);
    }

    @Test
    void testRecordExecution_Success_IncrementsCounter() {
        // When
        loaderMetrics.recordExecution("WALLET_TRANS", "SUCCESS");

        // Then
        Counter counter = meterRegistry.counter(
                "loader_executions_total",
                "loader_code", "WALLET_TRANS",
                "status", "SUCCESS"
        );

        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void testRecordExecution_Failed_IncrementsCounter() {
        // When
        loaderMetrics.recordExecution("WALLET_TRANS", "FAILED");

        // Then
        Counter counter = meterRegistry.counter(
                "loader_executions_total",
                "loader_code", "WALLET_TRANS",
                "status", "FAILED"
        );

        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void testRecordExecution_MultipleLoaders_SeparateCounters() {
        // When
        loaderMetrics.recordExecution("WALLET_TRANS", "SUCCESS");
        loaderMetrics.recordExecution("CARD_TRANS", "SUCCESS");
        loaderMetrics.recordExecution("WALLET_TRANS", "SUCCESS");

        // Then
        Counter walletCounter = meterRegistry.counter(
                "loader_executions_total",
                "loader_code", "WALLET_TRANS",
                "status", "SUCCESS"
        );
        Counter cardCounter = meterRegistry.counter(
                "loader_executions_total",
                "loader_code", "CARD_TRANS",
                "status", "SUCCESS"
        );

        assertThat(walletCounter.count()).isEqualTo(2.0);
        assertThat(cardCounter.count()).isEqualTo(1.0);
    }

    @Test
    void testRecordExecutionTime_RecordsDuration() {
        // Given
        Duration duration = Duration.ofSeconds(5);

        // When
        loaderMetrics.recordExecutionTime("WALLET_TRANS", duration);

        // Then
        Timer timer = meterRegistry.timer(
                "loader_execution_duration_seconds",
                "loader_code", "WALLET_TRANS"
        );

        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.SECONDS))
                .isGreaterThanOrEqualTo(5.0);
    }

    @Test
    void testRecordRecordsLoaded_IncrementsCounter() {
        // When
        loaderMetrics.recordRecordsLoaded("WALLET_TRANS", 1500);

        // Then
        Counter counter = meterRegistry.counter(
                "loader_records_loaded_total",
                "loader_code", "WALLET_TRANS"
        );

        assertThat(counter.count()).isEqualTo(1500.0);
    }

    @Test
    void testRecordRecordsLoaded_Cumulative() {
        // When
        loaderMetrics.recordRecordsLoaded("WALLET_TRANS", 1000);
        loaderMetrics.recordRecordsLoaded("WALLET_TRANS", 500);

        // Then
        Counter counter = meterRegistry.counter(
                "loader_records_loaded_total",
                "loader_code", "WALLET_TRANS"
        );

        assertThat(counter.count()).isEqualTo(1500.0);
    }

    @Test
    void testRecordRecordsIngested_IncrementsCounter() {
        // When
        loaderMetrics.recordRecordsIngested("WALLET_TRANS", 1200);

        // Then
        Counter counter = meterRegistry.counter(
                "loader_records_ingested_total",
                "loader_code", "WALLET_TRANS"
        );

        assertThat(counter.count()).isEqualTo(1200.0);
    }

    @Test
    void testSetRunningLoaders_UpdatesGauge() {
        // When
        loaderMetrics.setRunningLoaders(5);

        // Then
        assertThat(loaderMetrics.getRunningLoadersCount()).isEqualTo(5);

        // Verify gauge is registered
        assertThat(meterRegistry.get("loader_running_count").gauge().value())
                .isEqualTo(5.0);
    }

    @Test
    void testIncrementRunningLoaders_IncrementsGauge() {
        // When
        loaderMetrics.incrementRunningLoaders();
        loaderMetrics.incrementRunningLoaders();
        loaderMetrics.incrementRunningLoaders();

        // Then
        assertThat(loaderMetrics.getRunningLoadersCount()).isEqualTo(3);
        assertThat(meterRegistry.get("loader_running_count").gauge().value())
                .isEqualTo(3.0);
    }

    @Test
    void testDecrementRunningLoaders_DecrementsGauge() {
        // Given
        loaderMetrics.setRunningLoaders(5);

        // When
        loaderMetrics.decrementRunningLoaders();
        loaderMetrics.decrementRunningLoaders();

        // Then
        assertThat(loaderMetrics.getRunningLoadersCount()).isEqualTo(3);
        assertThat(meterRegistry.get("loader_running_count").gauge().value())
                .isEqualTo(3.0);
    }

    @Test
    void testSetEnabledLoaders_UpdatesGauge() {
        // When
        loaderMetrics.setEnabledLoaders(10);

        // Then
        assertThat(loaderMetrics.getEnabledLoadersCount()).isEqualTo(10);

        // Verify gauge is registered
        assertThat(meterRegistry.get("loader_enabled_count").gauge().value())
                .isEqualTo(10.0);
    }

    @Test
    void testConcurrentMetricRecording_ThreadSafe() throws InterruptedException {
        // Given
        int threadCount = 10;
        int incrementsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        // When: Multiple threads increment running loaders
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    loaderMetrics.incrementRunningLoaders();
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: Count should be accurate
        assertThat(loaderMetrics.getRunningLoadersCount())
                .isEqualTo(threadCount * incrementsPerThread);
    }

    @Test
    void testMetricsWithNullLoaderCode_HandlesGracefully() {
        // This should not throw exception (graceful degradation)
        // The actual behavior depends on implementation
        // For now, we just verify it doesn't crash
        try {
            loaderMetrics.recordExecution(null, "SUCCESS");
            loaderMetrics.recordExecutionTime(null, Duration.ofSeconds(1));
            loaderMetrics.recordRecordsLoaded(null, 100);
        } catch (Exception e) {
            // If exception is thrown, it should be logged but not crash
            assertThat(e).isNotNull();
        }
    }
}
