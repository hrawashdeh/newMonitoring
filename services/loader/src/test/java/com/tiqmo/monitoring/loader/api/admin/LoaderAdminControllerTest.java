package com.tiqmo.monitoring.loader.api.admin;

import com.tiqmo.monitoring.loader.api.admin.LoaderAdminController.AdjustTimestampRequest;
import com.tiqmo.monitoring.loader.api.admin.LoaderAdminController.AdjustTimestampResponse;
import com.tiqmo.monitoring.loader.api.admin.LoaderAdminController.ExecutionHistoryResponse;
import com.tiqmo.monitoring.loader.api.admin.LoaderAdminController.LoaderStatusResponse;
import com.tiqmo.monitoring.loader.api.admin.LoaderAdminController.PauseResumeResponse;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoadExecutionStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoadHistory;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoadStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.SourceDatabase;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoadHistoryRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LoaderAdminController (Rounds 14-16).
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class LoaderAdminControllerTest {

  @Mock
  private LoaderRepository loaderRepository;

  @Mock
  private LoadHistoryRepository loadHistoryRepository;

  private LoaderAdminController controller;

  @BeforeEach
  void setUp() {
    controller = new LoaderAdminController(loaderRepository, loadHistoryRepository);
  }

  // ==================== Adjust Timestamp Tests ====================

  @Test
  void adjustTimestamp_shouldReturnNotFound_whenLoaderDoesNotExist() {
    // Given
    when(loaderRepository.findByLoaderCode("NONEXISTENT")).thenReturn(Optional.empty());

    // When
    ResponseEntity<AdjustTimestampResponse> response = controller.adjustTimestamp(
        "NONEXISTENT",
        new AdjustTimestampRequest(Instant.now())
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(loaderRepository, never()).save(any());
  }

  @Test
  void adjustTimestamp_shouldResetToNull_whenRequestTimestampIsNull() {
    // Given
    Instant previousTimestamp = Instant.parse("2025-10-15T10:00:00Z");
    Loader loader = createTestLoader("TEST_LOADER", previousTimestamp);

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));
    when(loaderRepository.save(any())).thenReturn(loader);

    // When
    ResponseEntity<AdjustTimestampResponse> response = controller.adjustTimestamp(
        "TEST_LOADER",
        new AdjustTimestampRequest(null)
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().loaderCode()).isEqualTo("TEST_LOADER");
    assertThat(response.getBody().previousTimestamp()).isEqualTo(previousTimestamp);
    assertThat(response.getBody().newTimestamp()).isNull();
    assertThat(response.getBody().message()).contains("reset to null");

    // Verify loader was updated
    ArgumentCaptor<Loader> loaderCaptor = ArgumentCaptor.forClass(Loader.class);
    verify(loaderRepository).save(loaderCaptor.capture());
    assertThat(loaderCaptor.getValue().getLastLoadTimestamp()).isNull();
  }

  @Test
  void adjustTimestamp_shouldSetTimestamp_whenMovingForward() {
    // Given
    Instant previousTimestamp = Instant.parse("2025-10-15T10:00:00Z");
    Instant newTimestamp = Instant.parse("2025-10-20T12:00:00Z");
    Loader loader = createTestLoader("TEST_LOADER", previousTimestamp);

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));
    when(loaderRepository.save(any())).thenReturn(loader);

    // When
    ResponseEntity<AdjustTimestampResponse> response = controller.adjustTimestamp(
        "TEST_LOADER",
        new AdjustTimestampRequest(newTimestamp)
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().loaderCode()).isEqualTo("TEST_LOADER");
    assertThat(response.getBody().previousTimestamp()).isEqualTo(previousTimestamp);
    assertThat(response.getBody().newTimestamp()).isEqualTo(newTimestamp);
    assertThat(response.getBody().message()).contains("moved forwards");

    // Verify loader was updated
    ArgumentCaptor<Loader> loaderCaptor = ArgumentCaptor.forClass(Loader.class);
    verify(loaderRepository).save(loaderCaptor.capture());
    assertThat(loaderCaptor.getValue().getLastLoadTimestamp()).isEqualTo(newTimestamp);
  }

  @Test
  void adjustTimestamp_shouldSetTimestamp_whenMovingBackward() {
    // Given
    Instant previousTimestamp = Instant.parse("2025-10-20T12:00:00Z");
    Instant newTimestamp = Instant.parse("2025-10-15T10:00:00Z");
    Loader loader = createTestLoader("TEST_LOADER", previousTimestamp);

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));
    when(loaderRepository.save(any())).thenReturn(loader);

    // When
    ResponseEntity<AdjustTimestampResponse> response = controller.adjustTimestamp(
        "TEST_LOADER",
        new AdjustTimestampRequest(newTimestamp)
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().loaderCode()).isEqualTo("TEST_LOADER");
    assertThat(response.getBody().previousTimestamp()).isEqualTo(previousTimestamp);
    assertThat(response.getBody().newTimestamp()).isEqualTo(newTimestamp);
    assertThat(response.getBody().message()).contains("moved backwards");
    assertThat(response.getBody().message()).contains("reprocess historical data");

    // Verify loader was updated
    ArgumentCaptor<Loader> loaderCaptor = ArgumentCaptor.forClass(Loader.class);
    verify(loaderRepository).save(loaderCaptor.capture());
    assertThat(loaderCaptor.getValue().getLastLoadTimestamp()).isEqualTo(newTimestamp);
  }

  @Test
  void adjustTimestamp_shouldHandleNullToTimestamp() {
    // Given
    Loader loader = createTestLoader("TEST_LOADER", null);
    Instant newTimestamp = Instant.parse("2025-10-15T10:00:00Z");

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));
    when(loaderRepository.save(any())).thenReturn(loader);

    // When
    ResponseEntity<AdjustTimestampResponse> response = controller.adjustTimestamp(
        "TEST_LOADER",
        new AdjustTimestampRequest(newTimestamp)
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().loaderCode()).isEqualTo("TEST_LOADER");
    assertThat(response.getBody().previousTimestamp()).isNull();
    assertThat(response.getBody().newTimestamp()).isEqualTo(newTimestamp);
    assertThat(response.getBody().message()).contains("set from null");

    // Verify loader was updated
    ArgumentCaptor<Loader> loaderCaptor = ArgumentCaptor.forClass(Loader.class);
    verify(loaderRepository).save(loaderCaptor.capture());
    assertThat(loaderCaptor.getValue().getLastLoadTimestamp()).isEqualTo(newTimestamp);
  }

  @Test
  void adjustTimestamp_shouldHandleSameTimestamp() {
    // Given
    Instant timestamp = Instant.parse("2025-10-15T10:00:00Z");
    Loader loader = createTestLoader("TEST_LOADER", timestamp);

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));
    when(loaderRepository.save(any())).thenReturn(loader);

    // When
    ResponseEntity<AdjustTimestampResponse> response = controller.adjustTimestamp(
        "TEST_LOADER",
        new AdjustTimestampRequest(timestamp)
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).contains("unchanged");

    // Verify loader was still saved
    verify(loaderRepository).save(any());
  }

  // ==================== Get Status Tests ====================

  @Test
  void getStatus_shouldReturnNotFound_whenLoaderDoesNotExist() {
    // Given
    when(loaderRepository.findByLoaderCode("NONEXISTENT")).thenReturn(Optional.empty());

    // When
    ResponseEntity<LoaderStatusResponse> response = controller.getStatus("NONEXISTENT");

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void getStatus_shouldReturnLoaderStatus_whenLoaderExists() {
    // Given
    Instant lastLoadTimestamp = Instant.parse("2025-10-15T10:00:00Z");
    Instant failedSince = Instant.parse("2025-10-15T11:00:00Z");
    Loader loader = createTestLoader("TEST_LOADER", lastLoadTimestamp);
    loader.setLoadStatus(LoadStatus.FAILED);
    loader.setFailedSince(failedSince);
    loader.setEnabled(true);
    loader.setMinIntervalSeconds(60);
    loader.setMaxParallelExecutions(2);

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));

    // When
    ResponseEntity<LoaderStatusResponse> response = controller.getStatus("TEST_LOADER");

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().loaderCode()).isEqualTo("TEST_LOADER");
    assertThat(response.getBody().loadStatus()).isEqualTo("FAILED");
    assertThat(response.getBody().lastLoadTimestamp()).isEqualTo(lastLoadTimestamp);
    assertThat(response.getBody().failedSince()).isEqualTo(failedSince);
    assertThat(response.getBody().enabled()).isTrue();
    assertThat(response.getBody().minIntervalSeconds()).isEqualTo(60);
    assertThat(response.getBody().maxParallelExecutions()).isEqualTo(2);
  }

  // ==================== Round 15: Pause Tests ====================

  @Test
  void pauseLoader_shouldReturnNotFound_whenLoaderDoesNotExist() {
    // Given
    when(loaderRepository.findByLoaderCode("NONEXISTENT")).thenReturn(Optional.empty());

    // When
    ResponseEntity<PauseResumeResponse> response = controller.pauseLoader("NONEXISTENT");

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(loaderRepository, never()).save(any());
  }

  @Test
  void pauseLoader_shouldPauseIdleLoader() {
    // Given
    Loader loader = createTestLoader("TEST_LOADER", Instant.now());
    loader.setLoadStatus(LoadStatus.IDLE);

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));
    when(loaderRepository.save(any())).thenReturn(loader);

    // When
    ResponseEntity<PauseResumeResponse> response = controller.pauseLoader("TEST_LOADER");

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().loaderCode()).isEqualTo("TEST_LOADER");
    assertThat(response.getBody().previousStatus()).isEqualTo("IDLE");
    assertThat(response.getBody().newStatus()).isEqualTo("PAUSED");
    assertThat(response.getBody().message()).contains("was ready to execute");

    // Verify loader was updated
    ArgumentCaptor<Loader> loaderCaptor = ArgumentCaptor.forClass(Loader.class);
    verify(loaderRepository).save(loaderCaptor.capture());
    assertThat(loaderCaptor.getValue().getLoadStatus()).isEqualTo(LoadStatus.PAUSED);
  }

  @Test
  void pauseLoader_shouldPauseRunningLoader() {
    // Given
    Loader loader = createTestLoader("TEST_LOADER", Instant.now());
    loader.setLoadStatus(LoadStatus.RUNNING);

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));
    when(loaderRepository.save(any())).thenReturn(loader);

    // When
    ResponseEntity<PauseResumeResponse> response = controller.pauseLoader("TEST_LOADER");

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().previousStatus()).isEqualTo("RUNNING");
    assertThat(response.getBody().newStatus()).isEqualTo("PAUSED");
    assertThat(response.getBody().message()).contains("current execution will complete");

    verify(loaderRepository).save(any(Loader.class));
  }

  @Test
  void pauseLoader_shouldPauseFailedLoader() {
    // Given
    Loader loader = createTestLoader("TEST_LOADER", Instant.now());
    loader.setLoadStatus(LoadStatus.FAILED);
    loader.setFailedSince(Instant.now());

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));
    when(loaderRepository.save(any())).thenReturn(loader);

    // When
    ResponseEntity<PauseResumeResponse> response = controller.pauseLoader("TEST_LOADER");

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().previousStatus()).isEqualTo("FAILED");
    assertThat(response.getBody().newStatus()).isEqualTo("PAUSED");
    assertThat(response.getBody().message()).contains("failed state");
    assertThat(response.getBody().message()).contains("auto-recovery disabled");

    verify(loaderRepository).save(any(Loader.class));
  }

  @Test
  void pauseLoader_shouldBeIdempotent_whenAlreadyPaused() {
    // Given
    Loader loader = createTestLoader("TEST_LOADER", Instant.now());
    loader.setLoadStatus(LoadStatus.PAUSED);

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));

    // When
    ResponseEntity<PauseResumeResponse> response = controller.pauseLoader("TEST_LOADER");

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().previousStatus()).isEqualTo("PAUSED");
    assertThat(response.getBody().newStatus()).isEqualTo("PAUSED");
    assertThat(response.getBody().message()).contains("already paused");

    // Should not save (already in correct state)
    verify(loaderRepository, never()).save(any());
  }

  // ==================== Round 15: Resume Tests ====================

  @Test
  void resumeLoader_shouldReturnNotFound_whenLoaderDoesNotExist() {
    // Given
    when(loaderRepository.findByLoaderCode("NONEXISTENT")).thenReturn(Optional.empty());

    // When
    ResponseEntity<PauseResumeResponse> response = controller.resumeLoader("NONEXISTENT");

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(loaderRepository, never()).save(any());
  }

  @Test
  void resumeLoader_shouldResumePausedLoader() {
    // Given
    Loader loader = createTestLoader("TEST_LOADER", Instant.now());
    loader.setLoadStatus(LoadStatus.PAUSED);

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));
    when(loaderRepository.save(any())).thenReturn(loader);

    // When
    ResponseEntity<PauseResumeResponse> response = controller.resumeLoader("TEST_LOADER");

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().loaderCode()).isEqualTo("TEST_LOADER");
    assertThat(response.getBody().previousStatus()).isEqualTo("PAUSED");
    assertThat(response.getBody().newStatus()).isEqualTo("IDLE");
    assertThat(response.getBody().message()).contains("resumed");
    assertThat(response.getBody().message()).contains("within 10 seconds");

    // Verify loader was updated
    ArgumentCaptor<Loader> loaderCaptor = ArgumentCaptor.forClass(Loader.class);
    verify(loaderRepository).save(loaderCaptor.capture());
    assertThat(loaderCaptor.getValue().getLoadStatus()).isEqualTo(LoadStatus.IDLE);
  }

  @Test
  void resumeLoader_shouldReturnBadRequest_whenLoaderNotPaused() {
    // Given
    Loader loader = createTestLoader("TEST_LOADER", Instant.now());
    loader.setLoadStatus(LoadStatus.IDLE);

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));

    // When
    ResponseEntity<PauseResumeResponse> response = controller.resumeLoader("TEST_LOADER");

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().previousStatus()).isEqualTo("IDLE");
    assertThat(response.getBody().newStatus()).isEqualTo("IDLE");
    assertThat(response.getBody().message()).contains("Cannot resume");
    assertThat(response.getBody().message()).contains("not paused");

    // Should not save (invalid operation)
    verify(loaderRepository, never()).save(any());
  }

  @Test
  void resumeLoader_shouldReturnBadRequest_whenLoaderIsRunning() {
    // Given
    Loader loader = createTestLoader("TEST_LOADER", Instant.now());
    loader.setLoadStatus(LoadStatus.RUNNING);

    when(loaderRepository.findByLoaderCode("TEST_LOADER")).thenReturn(Optional.of(loader));

    // When
    ResponseEntity<PauseResumeResponse> response = controller.resumeLoader("TEST_LOADER");

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).contains("RUNNING");

    verify(loaderRepository, never()).save(any());
  }

  // ==================== Round 16: Execution History Tests ====================

  @Test
  void queryExecutionHistory_shouldReturnHistory_byLoaderCode() {
    // Given
    LoadHistory history1 = createTestLoadHistory(1L, "WALLET_TRANS", LoadExecutionStatus.SUCCESS);
    LoadHistory history2 = createTestLoadHistory(2L, "WALLET_TRANS", LoadExecutionStatus.SUCCESS);
    List<LoadHistory> historyList = List.of(history1, history2);

    when(loadHistoryRepository.findByLoaderCodeOrderByStartTimeDesc("WALLET_TRANS", 100))
        .thenReturn(historyList);

    // When
    ResponseEntity<List<ExecutionHistoryResponse>> response =
        controller.queryExecutionHistory("WALLET_TRANS", null, null, null, null, 100);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).hasSize(2);
    assertThat(response.getBody().get(0).loaderCode()).isEqualTo("WALLET_TRANS");
    assertThat(response.getBody().get(0).status()).isEqualTo("SUCCESS");

    verify(loadHistoryRepository).findByLoaderCodeOrderByStartTimeDesc("WALLET_TRANS", 100);
  }

  @Test
  void queryExecutionHistory_shouldReturnHistory_byStatus() {
    // Given
    LoadHistory history1 = createTestLoadHistory(1L, "WALLET_TRANS", LoadExecutionStatus.FAILED);
    LoadHistory history2 = createTestLoadHistory(2L, "USER_ACTIVITY", LoadExecutionStatus.FAILED);
    List<LoadHistory> historyList = List.of(history1, history2);

    when(loadHistoryRepository.findByStatusOrderByStartTimeDesc(LoadExecutionStatus.FAILED, 100))
        .thenReturn(historyList);

    // When
    ResponseEntity<List<ExecutionHistoryResponse>> response =
        controller.queryExecutionHistory(null, null, null, LoadExecutionStatus.FAILED, null, 100);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).hasSize(2);
    assertThat(response.getBody().get(0).status()).isEqualTo("FAILED");
    assertThat(response.getBody().get(1).status()).isEqualTo("FAILED");

    verify(loadHistoryRepository).findByStatusOrderByStartTimeDesc(LoadExecutionStatus.FAILED, 100);
  }

  @Test
  void queryExecutionHistory_shouldReturnHistory_byLoaderCodeAndStatus() {
    // Given
    LoadHistory history = createTestLoadHistory(1L, "WALLET_TRANS", LoadExecutionStatus.SUCCESS);
    List<LoadHistory> historyList = List.of(history);

    when(loadHistoryRepository.findByLoaderCodeAndStatusOrderByStartTimeDesc(
        "WALLET_TRANS", LoadExecutionStatus.SUCCESS, 50))
        .thenReturn(historyList);

    // When
    ResponseEntity<List<ExecutionHistoryResponse>> response =
        controller.queryExecutionHistory("WALLET_TRANS", null, null, LoadExecutionStatus.SUCCESS, null, 50);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().get(0).loaderCode()).isEqualTo("WALLET_TRANS");
    assertThat(response.getBody().get(0).status()).isEqualTo("SUCCESS");

    verify(loadHistoryRepository).findByLoaderCodeAndStatusOrderByStartTimeDesc(
        "WALLET_TRANS", LoadExecutionStatus.SUCCESS, 50);
  }

  @Test
  void queryExecutionHistory_shouldReturnHistory_byTimeRange() {
    // Given
    Instant fromTime = Instant.parse("2025-11-01T00:00:00Z");
    Instant toTime = Instant.parse("2025-11-01T23:59:59Z");

    LoadHistory history = createTestLoadHistory(1L, "WALLET_TRANS", LoadExecutionStatus.SUCCESS);
    List<LoadHistory> historyList = List.of(history);

    when(loadHistoryRepository.findByLoaderCodeAndStartTimeBetweenOrderByStartTimeDesc(
        "WALLET_TRANS", fromTime, toTime))
        .thenReturn(historyList);

    // When
    ResponseEntity<List<ExecutionHistoryResponse>> response =
        controller.queryExecutionHistory("WALLET_TRANS", fromTime, toTime, null, null, 100);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).hasSize(1);

    verify(loadHistoryRepository).findByLoaderCodeAndStartTimeBetweenOrderByStartTimeDesc(
        "WALLET_TRANS", fromTime, toTime);
  }

  @Test
  void queryExecutionHistory_shouldReturnRecentExecutions_whenNoFilters() {
    // Given
    LoadHistory history1 = createTestLoadHistory(1L, "WALLET_TRANS", LoadExecutionStatus.SUCCESS);
    LoadHistory history2 = createTestLoadHistory(2L, "USER_ACTIVITY", LoadExecutionStatus.SUCCESS);
    List<LoadHistory> historyList = List.of(history1, history2);

    when(loadHistoryRepository.findRecentExecutions(100))
        .thenReturn(historyList);

    // When
    ResponseEntity<List<ExecutionHistoryResponse>> response =
        controller.queryExecutionHistory(null, null, null, null, null, null);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).hasSize(2);

    verify(loadHistoryRepository).findRecentExecutions(100);
  }

  @Test
  void queryExecutionHistory_shouldCapLimit_atMaximum() {
    // Given
    when(loadHistoryRepository.findRecentExecutions(1000))
        .thenReturn(List.of());

    // When
    ResponseEntity<List<ExecutionHistoryResponse>> response =
        controller.queryExecutionHistory(null, null, null, null, null, 5000); // Request 5000

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Should be capped at 1000
    verify(loadHistoryRepository).findRecentExecutions(1000);
  }

  @Test
  void queryExecutionHistory_shouldDefaultLimit_whenNull() {
    // Given
    when(loadHistoryRepository.findRecentExecutions(100))
        .thenReturn(List.of());

    // When
    ResponseEntity<List<ExecutionHistoryResponse>> response =
        controller.queryExecutionHistory(null, null, null, null, null, null);

    // Then
    verify(loadHistoryRepository).findRecentExecutions(100); // Default is 100
  }

  // ==================== Helper Methods ====================

  private LoadHistory createTestLoadHistory(Long id, String loaderCode, LoadExecutionStatus status) {
    Instant now = Instant.now();
    return LoadHistory.builder()
        .id(id)
        .loaderCode(loaderCode)
        .sourceDatabaseCode("TEST_DB")
        .startTime(now.minusSeconds(60))
        .endTime(now)
        .durationSeconds(60L)
        .queryFromTime(now.minusSeconds(3600))
        .queryToTime(now)
        .status(status)
        .recordsLoaded(100L)
        .recordsIngested(100L)
        .errorMessage(status == LoadExecutionStatus.FAILED ? "Test error" : null)
        .replicaName("test-pod-1")
        .build();
  }

  // ==================== Loader Helper Methods ====================

  private Loader createTestLoader(String loaderCode, Instant lastLoadTimestamp) {
    SourceDatabase sourceDb = SourceDatabase.builder()
        .dbCode("TEST_DB")
        .build();

    return Loader.builder()
        .loaderCode(loaderCode)
        .sourceDatabase(sourceDb)
        .loaderSql("SELECT * FROM test")
        .minIntervalSeconds(10)
        .maxIntervalSeconds(60)
        .maxQueryPeriodSeconds(3600)
        .maxParallelExecutions(1)
        .enabled(true)
        .loadStatus(LoadStatus.IDLE)
        .lastLoadTimestamp(lastLoadTimestamp)
        .build();
  }
}
