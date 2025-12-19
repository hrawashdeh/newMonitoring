package com.tiqmo.monitoring.loader.service.execution;

import com.tiqmo.monitoring.loader.domain.loader.entity.LoadExecutionStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoadHistory;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoadStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoadHistoryRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import com.tiqmo.monitoring.loader.domain.signals.repo.SignalsHistoryRepository;
import com.tiqmo.monitoring.loader.dto.common.ErrorCode;
import com.tiqmo.monitoring.loader.exception.BusinessException;
import com.tiqmo.monitoring.loader.infra.ReplicaNameProvider;
import com.tiqmo.monitoring.loader.infra.db.SourceDbManager;
import com.tiqmo.monitoring.loader.metrics.LoaderMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of LoadExecutorService.
 *
 * <p>Complete data loading pipeline:
 * <ol>
 *   <li>Calculate query time window (TimeWindowCalculator)</li>
 *   <li>Build SQL query with time placeholders (QueryParameterReplacer)</li>
 *   <li>Execute query against source database (SourceDbManager)</li>
 *   <li>Transform results to SignalsHistory (DataTransformer)</li>
 *   <li>Ingest data to signals_history table (SignalsHistoryRepository)</li>
 * </ol>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultLoadExecutorService implements LoadExecutorService {

  private final LoaderRepository loaderRepository;
  private final LoadHistoryRepository loadHistoryRepository;
  private final SignalsHistoryRepository signalsHistoryRepository;
  private final ReplicaNameProvider replicaNameProvider;

  // Round 6-9 services (Core Pipeline)
  private final TimeWindowCalculator timeWindowCalculator;
  private final QueryParameterReplacer queryParameterReplacer;
  private final DataTransformer dataTransformer;
  private final SourceDbManager sourceDbManager;

  // Round 22: Custom Prometheus metrics
  private final LoaderMetrics loaderMetrics;

  // Issue #2.2: Configuration service for downtime detection threshold
  private final com.tiqmo.monitoring.loader.service.config.ConfigService configService;

  /**
   * Executes a loader with full data pipeline.
   *
   * <p><b>FIXED Issue #13:</b> Removed @Transactional from main execution method.
   * Transactions now only wrap persistence operations to avoid holding DB connections
   * during long-running source database queries and data transformations.
   *
   * @param loader Loader to execute
   * @return Load history with execution results
   * @throws BusinessException if loader is null or execution fails
   */
  @Override
  public LoadHistory executeLoader(Loader loader) {
    // Validation
    if (loader == null) {
      log.error("Loader is null");
      throw new BusinessException(
          ErrorCode.VALIDATION_REQUIRED_FIELD,
          "Loader cannot be null",
          "loader"
      );
    }

    MDC.put("loaderCode", loader.getLoaderCode());

    try {
      log.info("Starting loader execution | loaderCode={} | replica={} | lastLoad={}",
          loader.getLoaderCode(), replicaNameProvider.getReplicaName(),
          loader.getLastLoadTimestamp());

      Instant startTime = Instant.now();
      LoadHistory history = null;

      // Round 22: Increment running loaders count
      loaderMetrics.incrementRunningLoaders();

      try {
      // 1. Calculate time window first (needed for LoadHistory)
      TimeWindow window = timeWindowCalculator.calculateWindow(loader);
      log.info("Calculated time window for {}: from={}, to={}, duration={}s",
          loader.getLoaderCode(), window.fromTime(), window.toTime(), window.getDurationSeconds());

      // 2. Create LoadHistory record with RUNNING status (transactional)
      history = createRunningHistory(loader, startTime, window);
      history = saveHistoryAndLoaderAtStart(history, loader);

      // 4. Execute loader (REAL IMPLEMENTATION - Rounds 6-9)
      LoadExecutionResult result = executeLoaderReal(loader, window);

      // 5. Update history with success
      Instant endTime = Instant.now();
      updateHistorySuccess(history, result, startTime, endTime);

      // 6. Update loader state
      loader.setLoadStatus(LoadStatus.IDLE);
      loader.setLastLoadTimestamp(result.getQueryToTime());  // ALWAYS advance timestamp (even if 0 records)
      loader.setFailedSince(null); // Clear any previous failure

      // Issue #2.2: Track consecutive zero-record runs for downtime detection
      if (result.getRecordsLoaded() == 0) {
        // Increment counter for consecutive zero-record runs
        Integer currentCount = loader.getConsecutiveZeroRecordRuns() != null
                ? loader.getConsecutiveZeroRecordRuns() : 0;
        loader.setConsecutiveZeroRecordRuns(currentCount + 1);

        // Get threshold from configuration (default: 10)
        Integer maxZeroRecordRuns = configService.getConfigAsInt(
                "loader", "max-zero-record-runs", 10
        );

        // Log warning if threshold exceeded (possible prolonged downtime)
        if (loader.getConsecutiveZeroRecordRuns() > maxZeroRecordRuns) {
          log.warn("Loader {} has {} consecutive runs with 0 records loaded (threshold: {}) - " +
                          "possible prolonged source downtime or data issue. " +
                          "Time window: {} to {}",
                  loader.getLoaderCode(),
                  loader.getConsecutiveZeroRecordRuns(),
                  maxZeroRecordRuns,
                  result.getQueryFromTime(),
                  result.getQueryToTime());
        } else {
          log.info("Loader {} advanced past downtime period: {} → {} (0 records loaded, run {}/{})",
                  loader.getLoaderCode(),
                  result.getQueryFromTime(),
                  result.getQueryToTime(),
                  loader.getConsecutiveZeroRecordRuns(),
                  maxZeroRecordRuns);
        }
      } else {
        // Records loaded - reset consecutive zero-record counter
        if (loader.getConsecutiveZeroRecordRuns() != null && loader.getConsecutiveZeroRecordRuns() > 0) {
          log.info("Loader {} recovered from {} consecutive zero-record runs - loaded {} records",
                  loader.getLoaderCode(),
                  loader.getConsecutiveZeroRecordRuns(),
                  result.getRecordsLoaded());
        }
        loader.setConsecutiveZeroRecordRuns(0);
      }

      saveLoaderAfterSuccess(loader);

      // Round 22: Record success metrics
      Duration executionDuration = Duration.between(startTime, endTime);
      loaderMetrics.recordExecution(loader.getLoaderCode(), "SUCCESS");
      loaderMetrics.recordExecutionTime(loader.getLoaderCode(), executionDuration);
      loaderMetrics.recordRecordsLoaded(loader.getLoaderCode(), result.getRecordsLoaded());
      loaderMetrics.recordRecordsIngested(loader.getLoaderCode(), result.getRecordsIngested());
      loaderMetrics.decrementRunningLoaders();

      log.info("Loader execution completed | loaderCode={} | duration={}ms | loaded={} | ingested={}",
          loader.getLoaderCode(),
          Duration.between(startTime, Instant.now()).toMillis(),
          result.getRecordsLoaded(),
          result.getRecordsIngested());

        return history;

      } catch (Exception e) {
        log.error("Loader execution failed | loaderCode={}", loader.getLoaderCode(), e);

        // Update history with failure
        if (history != null) {
          Instant endTime = Instant.now();
          updateHistoryFailure(history, e, startTime, endTime);
        }

        // Update loader to FAILED status (transactional)
        loader.setLoadStatus(LoadStatus.FAILED);
        loader.setFailedSince(Instant.now());
        if (history != null) {
          history = saveHistoryAndLoaderAfterFailure(history, loader);
        } else {
          saveLoaderAfterSuccess(loader); // Reuse method for single loader save
        }

        // Round 22: Record failure metrics
        Instant endTime = Instant.now();
        Duration executionDuration = Duration.between(startTime, endTime);
        loaderMetrics.recordExecution(loader.getLoaderCode(), "FAILED");
        loaderMetrics.recordExecutionTime(loader.getLoaderCode(), executionDuration);
        loaderMetrics.decrementRunningLoaders();

        return history;
      }

    } finally {
      MDC.remove("loaderCode");
    }
  }

  /**
   * Executes a loader by loader code.
   *
   * @param loaderCode Loader code to execute
   * @return Load history with execution results
   * @throws BusinessException if loader not found
   */
  @Override
  @Transactional(readOnly = true)
  public LoadHistory executeLoader(String loaderCode) {
    log.debug("Executing loader by code | loaderCode={}", loaderCode);

    Loader loader = loaderRepository.findByLoaderCode(loaderCode)
        .orElseThrow(() -> {
          log.warn("Loader not found | loaderCode={}", loaderCode);
          return new BusinessException(
              ErrorCode.LOADER_NOT_FOUND,
              "Loader with code '" + loaderCode + "' not found"
          );
        });

    return executeLoader(loader);
  }

  /**
   * Creates initial LoadHistory record with RUNNING status.
   */
  private LoadHistory createRunningHistory(Loader loader, Instant startTime, TimeWindow window) {
    return LoadHistory.builder()
        .loaderCode(loader.getLoaderCode())
        .sourceDatabaseCode(loader.getSourceDatabase().getDbCode())
        .status(LoadExecutionStatus.RUNNING)
        .startTime(startTime)
        .queryFromTime(window.fromTime())
        .queryToTime(window.toTime())
        .replicaName(replicaNameProvider.getReplicaName())
        .build();
  }

  /**
   * Updates LoadHistory with successful execution results.
   */
  private void updateHistorySuccess(LoadHistory history,
                                     LoadExecutionResult result,
                                     Instant startTime,
                                     Instant endTime) {
    history.setStatus(LoadExecutionStatus.SUCCESS);
    history.setEndTime(endTime);
    history.setDurationSeconds(Duration.between(startTime, endTime).getSeconds());
    history.setQueryFromTime(result.getQueryFromTime());
    history.setQueryToTime(result.getQueryToTime());
    history.setRecordsLoaded(result.getRecordsLoaded());
    history.setRecordsIngested(result.getRecordsIngested());
    loadHistoryRepository.save(history);
  }

  /**
   * Updates LoadHistory with failure information.
   */
  private void updateHistoryFailure(LoadHistory history,
                                     Exception exception,
                                     Instant startTime,
                                     Instant endTime) {
    history.setStatus(LoadExecutionStatus.FAILED);
    history.setEndTime(endTime);
    history.setDurationSeconds(Duration.between(startTime, endTime).getSeconds());
    history.setErrorMessage(exception.getMessage());
    history.setStackTrace(getStackTraceAsString(exception));
    loadHistoryRepository.save(history);
  }

  /**
   * Converts exception stack trace to string.
   */
  private String getStackTraceAsString(Exception exception) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    exception.printStackTrace(pw);
    return sw.toString();
  }

  /**
   * REAL IMPLEMENTATION: Executes loader with complete data pipeline.
   *
   * <p><b>Pipeline Steps:</b>
   * <ol>
   *   <li><b>Round 6</b>: Calculate time window (TimeWindowCalculator)</li>
   *   <li><b>Round 7</b>: Build executable SQL (QueryParameterReplacer)</li>
   *   <li>Execute query against source database (SourceDbManager)</li>
   *   <li><b>Round 8</b>: Transform results (DataTransformer)</li>
   *   <li>Ingest to signals_history (SignalsHistoryRepository)</li>
   * </ol>
   *
   * @param loader the loader to execute
   * @return execution result with counts
   * @throws Exception if any step fails
   */
  private LoadExecutionResult executeLoaderReal(Loader loader, TimeWindow window) throws Exception {
    String loaderCode = loader.getLoaderCode();
    log.debug("Starting real execution for loader: {}", loaderCode);

    // Step 2: Build executable SQL (Round 7 + Issue #2.1: Timezone handling)
    String loaderSql = loader.getLoaderSql(); // This is encrypted, auto-decrypted by JPA
    Integer timezoneOffset = loader.getSourceTimezoneOffsetHours();
    String executableSql = queryParameterReplacer.replacePlaceholders(loaderSql, window, timezoneOffset);
    log.info("Built executable SQL for {} (timezone offset: {} hours): {}",
        loaderCode, timezoneOffset != null ? timezoneOffset : 0, executableSql);

    // Step 3: Execute query against source database
    String sourceDbCode = loader.getSourceDatabase().getDbCode();
    List<Map<String, Object>> rows = sourceDbManager.runQuery(sourceDbCode, executableSql);
    log.info("Query executed for {}: {} rows returned from source DB '{}'",
        loaderCode, rows.size(), sourceDbCode);

    // Build LoaderQueryResult
    LoaderQueryResult queryResult = new LoaderQueryResult(
        window.fromTime(),
        window.toTime(),
        rows,
        rows.size()
    );

    // Step 4: Transform results (Round 8 + Issue #2.1: Timezone normalization)
    List<SignalsHistory> signals = dataTransformer.transform(loaderCode, queryResult, timezoneOffset);
    log.debug("Transformed {} rows for {}: {} SignalsHistory entities created (timezone offset: {} hours)",
        rows.size(), loaderCode, signals.size(), timezoneOffset != null ? timezoneOffset : 0);

    // Step 5: Ingest to signals_history table
    List<SignalsHistory> ingested = signalsHistoryRepository.saveAll(signals);
    log.debug("Ingested {} signals for {} to signals_history table",
        ingested.size(), loaderCode);

    // Return result
    return LoadExecutionResult.builder()
        .queryFromTime(window.fromTime())
        .queryToTime(window.toTime())
        .recordsLoaded((long) rows.size())
        .recordsIngested((long) ingested.size())
        .build();
  }

  // ====================================================================================
  // Transactional helper methods (Issue #13 fix)
  // ====================================================================================

  /**
   * Saves history and updates loader to RUNNING status in a single transaction.
   * This ensures atomic start of execution tracking.
   *
   * @param history LoadHistory to save
   * @param loader Loader to update
   * @return Saved LoadHistory with generated ID
   */
  @Transactional
  private LoadHistory saveHistoryAndLoaderAtStart(LoadHistory history, Loader loader) {
    LoadHistory savedHistory = loadHistoryRepository.save(history);
    loader.setLoadStatus(LoadStatus.RUNNING);
    loaderRepository.save(loader);
    return savedHistory;
  }

  /**
   * Updates loader state after successful execution in a single transaction.
   *
   * @param loader Loader to update
   */
  @Transactional
  private void saveLoaderAfterSuccess(Loader loader) {
    loaderRepository.save(loader);
  }

  /**
   * Updates history and loader after failed execution in a single transaction.
   *
   * @param history LoadHistory to update
   * @param loader Loader to update
   * @return Updated LoadHistory
   */
  @Transactional
  private LoadHistory saveHistoryAndLoaderAfterFailure(LoadHistory history, Loader loader) {
    loaderRepository.save(loader);
    return loadHistoryRepository.save(history);
  }

  // ====================================================================================

  /**
   * Result of loader execution (internal DTO).
   */
  @lombok.Data
  @lombok.Builder
  private static class LoadExecutionResult {
    private Instant queryFromTime;
    private Instant queryToTime;
    private Long recordsLoaded;
    private Long recordsIngested;
  }
}
