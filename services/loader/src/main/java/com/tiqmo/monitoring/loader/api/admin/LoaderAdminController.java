package com.tiqmo.monitoring.loader.api.admin;

import com.tiqmo.monitoring.loader.domain.loader.entity.LoadExecutionStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoadHistory;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoadStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoadHistoryRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.infra.config.ApiKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service ID: ldr (Loader Service), Controller ID: admn (Admin Controller)
 *
 * <p>Admin API for loader operational management (Rounds 14-16).
 *
 * <p>Provides administrative endpoints for:
 * <ul>
 *   <li>Adjusting lastLoadTimestamp to reprocess historical data (Round 14)</li>
 *   <li>Pause/resume loader execution (Round 15)</li>
 *   <li>Query execution history with filters (Round 16)</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0 (Rounds 14-16)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ldr/admn")
@RequiredArgsConstructor
public class LoaderAdminController {

  private final LoaderRepository loaderRepository;
  private final LoadHistoryRepository loadHistoryRepository;

  /**
   * Request to adjust lastLoadTimestamp.
   *
   * @param timestamp new timestamp (null = reset to beginning)
   */
  public record AdjustTimestampRequest(Instant timestamp) {}

  /**
   * Response for timestamp adjustment.
   *
   * @param loaderCode loader code
   * @param previousTimestamp timestamp before adjustment
   * @param newTimestamp timestamp after adjustment
   * @param message human-readable message
   */
  public record AdjustTimestampResponse(
      String loaderCode,
      Instant previousTimestamp,
      Instant newTimestamp,
      String message
  ) {}

  /**
   * Round 14: Adjust lastLoadTimestamp for a loader.
   *
   * <p><b>Use Cases:</b>
   * <ul>
   *   <li>Force immediate re-execution: Set timestamp to null or very old value</li>
   *   <li>Reprocess historical data: Set timestamp backwards</li>
   *   <li>Skip forward: Set timestamp to future or specific point</li>
   * </ul>
   *
   * <p><b>Example Requests:</b>
   * <pre>
   * POST /ops/v1/admin/loaders/WALLET_TRANS/adjust-timestamp
   * { "timestamp": null }  // Reset to beginning
   *
   * POST /ops/v1/admin/loaders/WALLET_TRANS/adjust-timestamp
   * { "timestamp": "2025-10-01T00:00:00Z" }  // Set to specific time
   * </pre>
   *
   * <p><b>Safety Notes:</b>
   * <ul>
   *   <li>Adjusting backwards may cause duplicate data (check purgeStrategy)</li>
   *   <li>Loader continues executing based on new timestamp</li>
   *   <li>No data is automatically deleted (manual purge if needed)</li>
   * </ul>
   *
   * @param loaderCode the loader code
   * @param request the adjustment request (timestamp or null)
   * @return adjustment result
   */
  @PostMapping("/{loaderCode}/adjust-timestamp")
  @ApiKey(value = "ldr.admin.adjustTimestamp", description = "Adjust loader lastLoadTimestamp", tags = {"admin"})
  public ResponseEntity<AdjustTimestampResponse> adjustTimestamp(
      @PathVariable String loaderCode,
      @RequestBody AdjustTimestampRequest request
  ) {
    log.info("Round 14: Adjusting timestamp for loader: {} to: {}",
        loaderCode, request.timestamp);

    Optional<Loader> loaderOpt = loaderRepository.findByLoaderCode(loaderCode);

    if (loaderOpt.isEmpty()) {
      log.warn("Round 14: Loader not found: {}", loaderCode);
      return ResponseEntity.notFound().build();
    }

    Loader loader = loaderOpt.get();
    Instant previousTimestamp = loader.getLastLoadTimestamp();

    // Update timestamp
    loader.setLastLoadTimestamp(request.timestamp);
    loaderRepository.save(loader);

    String message = buildAdjustmentMessage(previousTimestamp, request.timestamp);

    log.info("Round 14: Timestamp adjusted for loader: {} from {} to {}",
        loaderCode, previousTimestamp, request.timestamp);

    AdjustTimestampResponse response = new AdjustTimestampResponse(
        loaderCode,
        previousTimestamp,
        request.timestamp,
        message
    );

    return ResponseEntity.ok(response);
  }

  /**
   * Get current loader status including timestamp.
   *
   * @param loaderCode the loader code
   * @return loader status
   */
  @GetMapping("/{loaderCode}/status")
  @ApiKey(value = "ldr.admin.status", description = "Get loader operational status", tags = {"admin"})
  public ResponseEntity<LoaderStatusResponse> getStatus(@PathVariable String loaderCode) {
    Optional<Loader> loaderOpt = loaderRepository.findByLoaderCode(loaderCode);

    if (loaderOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Loader loader = loaderOpt.get();

    LoaderStatusResponse response = new LoaderStatusResponse(
        loader.getLoaderCode(),
        loader.getLoadStatus().name(),
        loader.getLastLoadTimestamp(),
        loader.getFailedSince(),
        loader.isEnabled(),
        loader.getMinIntervalSeconds(),
        loader.getMaxParallelExecutions()
    );

    return ResponseEntity.ok(response);
  }

  /**
   * Response for loader status query.
   */
  public record LoaderStatusResponse(
      String loaderCode,
      String loadStatus,
      Instant lastLoadTimestamp,
      Instant failedSince,
      boolean enabled,
      int minIntervalSeconds,
      int maxParallelExecutions
  ) {}

  // ==================== Round 15: Pause/Resume APIs ====================

  /**
   * Response for pause/resume operations.
   */
  public record PauseResumeResponse(
      String loaderCode,
      String previousStatus,
      String newStatus,
      String message
  ) {}

  /**
   * Round 15: Pause a loader.
   *
   * <p>Changes loader status to PAUSED. The scheduler will skip PAUSED loaders
   * during execution. The loader will remain paused until explicitly resumed.
   *
   * <p><b>Use Cases:</b>
   * <ul>
   *   <li>Maintenance: Pause during source database maintenance</li>
   *   <li>Incident response: Pause if bad data is being ingested</li>
   *   <li>Troubleshooting: Pause to investigate issues</li>
   *   <li>Resource control: Pause low-priority loaders during peak hours</li>
   * </ul>
   *
   * <p><b>Safety Notes:</b>
   * <ul>
   *   <li>If loader is currently RUNNING, it completes current execution before pause takes effect</li>
   *   <li>FAILED loaders can be paused (won't auto-recover while paused)</li>
   *   <li>Pause preserves lastLoadTimestamp (resumes from where it left off)</li>
   * </ul>
   *
   * @param loaderCode the loader code
   * @return pause result
   */
  @PostMapping("/{loaderCode}/pause")
  @ApiKey(value = "ldr.admin.pause", description = "Pause a loader from execution", tags = {"admin"})
  public ResponseEntity<PauseResumeResponse> pauseLoader(@PathVariable String loaderCode) {
    log.info("Round 15: Pausing loader: {}", loaderCode);

    Optional<Loader> loaderOpt = loaderRepository.findByLoaderCode(loaderCode);

    if (loaderOpt.isEmpty()) {
      log.warn("Round 15: Loader not found: {}", loaderCode);
      return ResponseEntity.notFound().build();
    }

    Loader loader = loaderOpt.get();
    LoadStatus previousStatus = loader.getLoadStatus();

    // Already paused?
    if (previousStatus == LoadStatus.PAUSED) {
      log.debug("Round 15: Loader {} is already PAUSED", loaderCode);
      return ResponseEntity.ok(new PauseResumeResponse(
          loaderCode,
          previousStatus.name(),
          LoadStatus.PAUSED.name(),
          "Loader is already paused"
      ));
    }

    // Pause the loader
    loader.setLoadStatus(LoadStatus.PAUSED);
    loaderRepository.save(loader);

    log.info("Round 15: Loader {} paused (previous status: {})", loaderCode, previousStatus);

    String message = buildPauseMessage(previousStatus);

    return ResponseEntity.ok(new PauseResumeResponse(
        loaderCode,
        previousStatus.name(),
        LoadStatus.PAUSED.name(),
        message
    ));
  }

  /**
   * Round 15: Resume a paused loader.
   *
   * <p>Changes loader status from PAUSED to IDLE. The scheduler will resume
   * executing the loader on the next cycle (within 10 seconds).
   *
   * <p><b>Resume Behavior:</b>
   * <ul>
   *   <li>Status changed to IDLE (ready to execute)</li>
   *   <li>Resumes from lastLoadTimestamp (no data loss)</li>
   *   <li>Scheduler picks up within 10 seconds</li>
   *   <li>Normal execution rules apply (intervals, locks, etc.)</li>
   * </ul>
   *
   * <p><b>Safety Notes:</b>
   * <ul>
   *   <li>Only works on PAUSED loaders (400 error otherwise)</li>
   *   <li>Does not force immediate execution (respects minIntervalSeconds)</li>
   *   <li>Use adjust-timestamp API if you need to reprocess or skip forward</li>
   * </ul>
   *
   * @param loaderCode the loader code
   * @return resume result
   */
  @PostMapping("/{loaderCode}/resume")
  @ApiKey(value = "ldr.admin.resume", description = "Resume a paused loader", tags = {"admin"})
  public ResponseEntity<PauseResumeResponse> resumeLoader(@PathVariable String loaderCode) {
    log.info("Round 15: Resuming loader: {}", loaderCode);

    Optional<Loader> loaderOpt = loaderRepository.findByLoaderCode(loaderCode);

    if (loaderOpt.isEmpty()) {
      log.warn("Round 15: Loader not found: {}", loaderCode);
      return ResponseEntity.notFound().build();
    }

    Loader loader = loaderOpt.get();
    LoadStatus previousStatus = loader.getLoadStatus();

    // Not paused?
    if (previousStatus != LoadStatus.PAUSED) {
      log.warn("Round 15: Cannot resume loader {} - not in PAUSED state (current: {})",
          loaderCode, previousStatus);

      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new PauseResumeResponse(
          loaderCode,
          previousStatus.name(),
          previousStatus.name(),
          "Cannot resume - loader is not paused (current status: " + previousStatus + ")"
      ));
    }

    // Resume the loader (set to IDLE)
    loader.setLoadStatus(LoadStatus.IDLE);
    loaderRepository.save(loader);

    log.info("Round 15: Loader {} resumed to IDLE", loaderCode);

    return ResponseEntity.ok(new PauseResumeResponse(
        loaderCode,
        LoadStatus.PAUSED.name(),
        LoadStatus.IDLE.name(),
        "Loader resumed and will execute on next scheduler cycle (within 10 seconds)"
    ));
  }

  // ==================== Round 16: Execution History API ====================

  /**
   * Response DTO for execution history records.
   */
  public record ExecutionHistoryResponse(
      Long id,
      String loaderCode,
      String sourceDatabaseCode,
      Instant startTime,
      Instant endTime,
      Long durationSeconds,
      Instant queryFromTime,
      Instant queryToTime,
      String status,
      Long recordsLoaded,
      Long recordsIngested,
      String errorMessage,
      String replicaName
  ) {
    public static ExecutionHistoryResponse from(LoadHistory history) {
      return new ExecutionHistoryResponse(
          history.getId(),
          history.getLoaderCode(),
          history.getSourceDatabaseCode(),
          history.getStartTime(),
          history.getEndTime(),
          history.getDurationSeconds(),
          history.getQueryFromTime(),
          history.getQueryToTime(),
          history.getStatus().name(),
          history.getRecordsLoaded(),
          history.getRecordsIngested(),
          history.getErrorMessage(),
          history.getReplicaName()
      );
    }
  }

  /**
   * Round 16: Query execution history with filters.
   *
   * <p>Returns execution history records with optional filters:
   * <ul>
   *   <li><b>loaderCode</b>: Filter by specific loader</li>
   *   <li><b>startTimeFrom/startTimeTo</b>: Time range filter</li>
   *   <li><b>status</b>: Filter by execution status (SUCCESS, FAILED, RUNNING, PARTIAL)</li>
   *   <li><b>replicaName</b>: Filter by replica/pod name</li>
   *   <li><b>limit</b>: Max results (default: 100, max: 1000)</li>
   * </ul>
   *
   * <p><b>Example Queries:</b>
   * <pre>
   * GET /ops/v1/admin/loaders/history?loaderCode=WALLET_TRANS&limit=50
   * GET /ops/v1/admin/loaders/history?status=FAILED&startTimeFrom=2025-11-01T00:00:00Z
   * GET /ops/v1/admin/loaders/history?replicaName=loader-pod-1&limit=20
   * </pre>
   *
   * <p><b>Use Cases:</b>
   * <ul>
   *   <li>Troubleshooting: Find failed executions for a loader</li>
   *   <li>Monitoring: Check recent execution performance</li>
   *   <li>Auditing: Review execution history for compliance</li>
   *   <li>Analysis: Identify patterns in execution times or failures</li>
   * </ul>
   *
   * @param loaderCode optional loader code filter
   * @param startTimeFrom optional start time lower bound
   * @param startTimeTo optional start time upper bound
   * @param status optional execution status filter
   * @param replicaName optional replica name filter
   * @param limit max results (default: 100, max: 1000)
   * @return list of execution history records
   */
  @GetMapping("/history")
  @ApiKey(value = "ldr.admin.history", description = "Query loader execution history", tags = {"admin"})
  public ResponseEntity<List<ExecutionHistoryResponse>> queryExecutionHistory(
      @RequestParam(required = false) String loaderCode,
      @RequestParam(required = false) Instant startTimeFrom,
      @RequestParam(required = false) Instant startTimeTo,
      @RequestParam(required = false) LoadExecutionStatus status,
      @RequestParam(required = false) String replicaName,
      @RequestParam(required = false, defaultValue = "100") Integer limit
  ) {
    log.info("Round 16: Querying execution history - loaderCode: {}, startTimeFrom: {}, " +
            "startTimeTo: {}, status: {}, replicaName: {}, limit: {}",
        loaderCode, startTimeFrom, startTimeTo, status, replicaName, limit);

    // Validate and cap limit
    if (limit == null || limit < 1) {
      limit = 100;
    }
    if (limit > 1000) {
      limit = 1000;
    }

    List<LoadHistory> results;

    // Query logic based on filters
    if (loaderCode != null && startTimeFrom != null && startTimeTo != null) {
      // Loader + time range filter
      results = loadHistoryRepository.findByLoaderCodeAndStartTimeBetweenOrderByStartTimeDesc(
          loaderCode, startTimeFrom, startTimeTo
      );
    } else if (loaderCode != null && status != null) {
      // Loader + status filter
      results = loadHistoryRepository.findByLoaderCodeAndStatusOrderByStartTimeDesc(
          loaderCode, status, limit
      );
    } else if (loaderCode != null) {
      // Loader only filter
      results = loadHistoryRepository.findByLoaderCodeOrderByStartTimeDesc(
          loaderCode, limit
      );
    } else if (status != null && startTimeFrom != null) {
      // Status + time filter
      results = loadHistoryRepository.findByStatusAndStartTimeAfterOrderByStartTimeDesc(
          status, startTimeFrom, limit
      );
    } else if (status != null) {
      // Status only filter
      results = loadHistoryRepository.findByStatusOrderByStartTimeDesc(
          status, limit
      );
    } else if (replicaName != null) {
      // Replica filter
      results = loadHistoryRepository.findRecentByReplicaName(replicaName, limit);
    } else if (startTimeFrom != null && startTimeTo != null) {
      // Time range only
      results = loadHistoryRepository.findByStartTimeBetweenOrderByStartTimeDesc(
          startTimeFrom, startTimeTo, limit
      );
    } else {
      // No filters - return recent executions
      results = loadHistoryRepository.findRecentExecutions(limit);
    }

    // Apply post-filter for combinations not handled by repository
    if (status != null && results != null) {
      results = results.stream()
          .filter(h -> h.getStatus() == status)
          .limit(limit)
          .collect(Collectors.toList());
    }

    if (replicaName != null && results != null && loaderCode != null) {
      results = results.stream()
          .filter(h -> replicaName.equals(h.getReplicaName()))
          .limit(limit)
          .collect(Collectors.toList());
    }

    // Convert to DTOs
    List<ExecutionHistoryResponse> response = results.stream()
        .map(ExecutionHistoryResponse::from)
        .limit(limit)
        .collect(Collectors.toList());

    log.info("Round 16: Returning {} execution history records", response.size());

    return ResponseEntity.ok(response);
  }

  // ==================== Helper Methods ====================

  /**
   * Build human-readable pause message.
   */
  private String buildPauseMessage(LoadStatus previousStatus) {
    return switch (previousStatus) {
      case IDLE -> "Loader paused (was ready to execute)";
      case RUNNING -> "Loader paused (current execution will complete, then pause takes effect)";
      case FAILED -> "Loader paused (was in failed state, auto-recovery disabled while paused)";
      case PAUSED -> "Loader is already paused"; // Shouldn't reach here
    };
  }

  /**
   * Build human-readable adjustment message.
   */
  private String buildAdjustmentMessage(Instant previous, Instant newTimestamp) {
    if (previous == null && newTimestamp == null) {
      return "Timestamp remains null (loader will start from default lookback)";
    } else if (previous == null && newTimestamp != null) {
      return "Timestamp set from null to " + newTimestamp + " (loader will start from this point)";
    } else if (previous != null && newTimestamp == null) {
      return "Timestamp reset to null (loader will reprocess from beginning with default lookback)";
    } else {
      // Both non-null
      if (newTimestamp.isBefore(previous)) {
        return "Timestamp moved backwards (will reprocess historical data from " + newTimestamp + ")";
      } else if (newTimestamp.isAfter(previous)) {
        return "Timestamp moved forwards (will skip from " + previous + " to " + newTimestamp + ")";
      } else {
        return "Timestamp unchanged";
      }
    }
  }
}
