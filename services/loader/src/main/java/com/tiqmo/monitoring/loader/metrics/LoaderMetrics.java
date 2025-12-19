package com.tiqmo.monitoring.loader.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom Prometheus metrics for ETL Loader monitoring.
 *
 * <p>Provides business-level metrics for loader executions:
 * <ul>
 *   <li><b>loader_executions_total</b>: Counter of total executions by status</li>
 *   <li><b>loader_execution_duration_seconds</b>: Timer for execution duration</li>
 *   <li><b>loader_records_loaded_total</b>: Counter of records loaded from source</li>
 *   <li><b>loader_records_ingested_total</b>: Counter of records ingested to signals_history</li>
 *   <li><b>loader_running_count</b>: Gauge of currently running loaders</li>
 *   <li><b>loader_enabled_count</b>: Gauge of enabled loaders</li>
 * </ul>
 *
 * <p><b>Round 22 Implementation</b> - API Gateway + Custom Metrics
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0 (Round 22)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoaderMetrics {

    private final MeterRegistry meterRegistry;

    // Gauges state (using AtomicInteger for thread-safety)
    private final AtomicInteger runningLoadersCount = new AtomicInteger(0);
    private final AtomicInteger enabledLoadersCount = new AtomicInteger(0);

    /**
     * Records a loader execution completion with its status.
     *
     * <p>Metric: <code>loader_executions_total</code>
     * <p>Type: Counter
     * <p>Tags:
     * <ul>
     *   <li>loader_code: The unique loader identifier</li>
     *   <li>status: SUCCESS or FAILED</li>
     * </ul>
     *
     * @param loaderCode the loader code
     * @param status execution status (SUCCESS, FAILED)
     */
    public void recordExecution(String loaderCode, String status) {
        try {
            Counter.builder("loader_executions_total")
                   .description("Total number of loader executions by status")
                   .tag("loader_code", loaderCode)
                   .tag("status", status)  // SUCCESS, FAILED
                   .register(meterRegistry)
                   .increment();

            log.debug("Recorded execution metric: loader={}, status={}", loaderCode, status);
        } catch (Exception e) {
            log.warn("Failed to record execution metric for loader {}: {}", loaderCode, e.getMessage());
        }
    }

    /**
     * Records the execution duration of a loader.
     *
     * <p>Metric: <code>loader_execution_duration_seconds</code>
     * <p>Type: Timer
     * <p>Tags:
     * <ul>
     *   <li>loader_code: The unique loader identifier</li>
     * </ul>
     *
     * @param loaderCode the loader code
     * @param duration execution duration
     */
    public void recordExecutionTime(String loaderCode, Duration duration) {
        try {
            Timer.builder("loader_execution_duration_seconds")
                 .description("Loader execution duration in seconds")
                 .tag("loader_code", loaderCode)
                 .register(meterRegistry)
                 .record(duration);

            log.debug("Recorded execution time metric: loader={}, duration={}s",
                    loaderCode, duration.getSeconds());
        } catch (Exception e) {
            log.warn("Failed to record execution time metric for loader {}: {}", loaderCode, e.getMessage());
        }
    }

    /**
     * Records the number of records loaded from source database.
     *
     * <p>Metric: <code>loader_records_loaded_total</code>
     * <p>Type: Counter
     * <p>Tags:
     * <ul>
     *   <li>loader_code: The unique loader identifier</li>
     * </ul>
     *
     * @param loaderCode the loader code
     * @param count number of records loaded
     */
    public void recordRecordsLoaded(String loaderCode, long count) {
        try {
            Counter.builder("loader_records_loaded_total")
                   .description("Total number of records loaded from source database")
                   .tag("loader_code", loaderCode)
                   .register(meterRegistry)
                   .increment(count);

            log.debug("Recorded records loaded metric: loader={}, count={}", loaderCode, count);
        } catch (Exception e) {
            log.warn("Failed to record records loaded metric for loader {}: {}", loaderCode, e.getMessage());
        }
    }

    /**
     * Records the number of records ingested to signals_history table.
     *
     * <p>Metric: <code>loader_records_ingested_total</code>
     * <p>Type: Counter
     * <p>Tags:
     * <ul>
     *   <li>loader_code: The unique loader identifier</li>
     * </ul>
     *
     * @param loaderCode the loader code
     * @param count number of records ingested
     */
    public void recordRecordsIngested(String loaderCode, long count) {
        try {
            Counter.builder("loader_records_ingested_total")
                   .description("Total number of records ingested to signals_history")
                   .tag("loader_code", loaderCode)
                   .register(meterRegistry)
                   .increment(count);

            log.debug("Recorded records ingested metric: loader={}, count={}", loaderCode, count);
        } catch (Exception e) {
            log.warn("Failed to record records ingested metric for loader {}: {}", loaderCode, e.getMessage());
        }
    }

    /**
     * Updates the gauge of currently running loaders.
     *
     * <p>Metric: <code>loader_running_count</code>
     * <p>Type: Gauge
     *
     * @param count current number of running loaders
     */
    public void setRunningLoaders(int count) {
        try {
            runningLoadersCount.set(count);

            // Register gauge if not already registered
            meterRegistry.gauge("loader_running_count",
                    runningLoadersCount,
                    AtomicInteger::get);

            log.debug("Updated running loaders count: {}", count);
        } catch (Exception e) {
            log.warn("Failed to update running loaders count: {}", e.getMessage());
        }
    }

    /**
     * Increments the count of running loaders.
     */
    public void incrementRunningLoaders() {
        int newCount = runningLoadersCount.incrementAndGet();
        meterRegistry.gauge("loader_running_count",
                runningLoadersCount,
                AtomicInteger::get);
        log.debug("Incremented running loaders count to: {}", newCount);
    }

    /**
     * Decrements the count of running loaders.
     */
    public void decrementRunningLoaders() {
        int newCount = runningLoadersCount.decrementAndGet();
        meterRegistry.gauge("loader_running_count",
                runningLoadersCount,
                AtomicInteger::get);
        log.debug("Decremented running loaders count to: {}", newCount);
    }

    /**
     * Updates the gauge of enabled loaders.
     *
     * <p>Metric: <code>loader_enabled_count</code>
     * <p>Type: Gauge
     *
     * @param count current number of enabled loaders
     */
    public void setEnabledLoaders(int count) {
        try {
            enabledLoadersCount.set(count);

            // Register gauge if not already registered
            meterRegistry.gauge("loader_enabled_count",
                    enabledLoadersCount,
                    AtomicInteger::get);

            log.debug("Updated enabled loaders count: {}", count);
        } catch (Exception e) {
            log.warn("Failed to update enabled loaders count: {}", e.getMessage());
        }
    }

    /**
     * Gets the current count of running loaders.
     *
     * @return current running loaders count
     */
    public int getRunningLoadersCount() {
        return runningLoadersCount.get();
    }

    /**
     * Gets the current count of enabled loaders.
     *
     * @return current enabled loaders count
     */
    public int getEnabledLoadersCount() {
        return enabledLoadersCount.get();
    }
}
