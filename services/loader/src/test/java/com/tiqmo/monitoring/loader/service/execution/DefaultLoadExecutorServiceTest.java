package com.tiqmo.monitoring.loader.service.execution;

import com.tiqmo.monitoring.loader.domain.loader.entity.*;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoadHistoryRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.infra.ReplicaNameProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DefaultLoadExecutorService.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class DefaultLoadExecutorServiceTest {

  @Mock
  private LoaderRepository loaderRepository;

  @Mock
  private LoadHistoryRepository loadHistoryRepository;

  @Mock
  private com.tiqmo.monitoring.loader.domain.signals.repo.SignalsHistoryRepository signalsHistoryRepository;

  @Mock
  private ReplicaNameProvider replicaNameProvider;

  @Mock
  private TimeWindowCalculator timeWindowCalculator;

  @Mock
  private QueryParameterReplacer queryParameterReplacer;

  @Mock
  private DataTransformer dataTransformer;

  @Mock
  private com.tiqmo.monitoring.loader.infra.db.SourceDbManager sourceDbManager;

  @Mock
  private com.tiqmo.monitoring.loader.metrics.LoaderMetrics loaderMetrics;

  @Mock
  private com.tiqmo.monitoring.loader.service.config.ConfigService configService;

  @InjectMocks
  private DefaultLoadExecutorService executorService;

  private Loader testLoader;
  private LoadHistory testHistory;

  @BeforeEach
  void setUp() {
    // Setup test source database
    SourceDatabase sourceDb = SourceDatabase.builder()
        .dbCode("TEST_SOURCE")
        .ip("localhost")
        .port(5432)
        .dbName("testdb")
        .userName("testuser")
        .passWord("testpass")
        .build();

    // Setup test loader
    testLoader = Loader.builder()
        .id(1L)
        .loaderCode("TEST_LOADER")
        .loaderSql("SELECT * FROM test")
        .minIntervalSeconds(10)
        .maxIntervalSeconds(60)
        .maxQueryPeriodSeconds(432000)
        .maxParallelExecutions(1)
        .loadStatus(LoadStatus.IDLE)
        .enabled(true)
        .sourceDatabase(sourceDb)
        .lastLoadTimestamp(Instant.now().minusSeconds(3600))
        .build();

    // Setup test history
    testHistory = LoadHistory.builder()
        .id(1L)
        .loaderCode("TEST_LOADER")
        .status(LoadExecutionStatus.RUNNING)
        .startTime(Instant.now())
        .replicaName("test-replica")
        .build();

    // Default mock behaviors
    when(replicaNameProvider.getReplicaName()).thenReturn("test-replica");
    when(loadHistoryRepository.save(any(LoadHistory.class))).thenAnswer(i -> i.getArgument(0));
    when(loaderRepository.save(any(Loader.class))).thenAnswer(i -> i.getArgument(0));

    // Mock the execution pipeline (Issue #13 fix - transaction management)
    TimeWindow testWindow = new TimeWindow(
        Instant.now().minusSeconds(3600),
        Instant.now()
    );
    when(timeWindowCalculator.calculateWindow(any(Loader.class))).thenReturn(testWindow);
    when(queryParameterReplacer.replacePlaceholders(anyString(), any(TimeWindow.class), any(Integer.class)))
        .thenReturn("SELECT * FROM test WHERE ts >= 123 AND ts < 456");
    when(sourceDbManager.runQuery(anyString(), anyString())).thenReturn(java.util.List.of());
    when(dataTransformer.transform(anyString(), any(LoaderQueryResult.class), any(Integer.class)))
        .thenReturn(java.util.List.of());
    when(signalsHistoryRepository.saveAll(any())).thenReturn(java.util.List.of());
    when(configService.getConfigAsInt(anyString(), anyString(), anyInt())).thenReturn(10);

    // Mock metrics (no-op for unit tests)
    doNothing().when(loaderMetrics).incrementRunningLoaders();
    doNothing().when(loaderMetrics).decrementRunningLoaders();
    doNothing().when(loaderMetrics).recordExecution(anyString(), anyString());
    doNothing().when(loaderMetrics).recordExecutionTime(anyString(), any());
    doNothing().when(loaderMetrics).recordRecordsLoaded(anyString(), anyLong());
    doNothing().when(loaderMetrics).recordRecordsIngested(anyString(), anyLong());
  }

  @Test
  void testExecuteLoader_Success() {
    // Arrange
    when(loadHistoryRepository.save(any(LoadHistory.class))).thenReturn(testHistory);

    // Act
    LoadHistory result = executorService.executeLoader(testLoader);

    // Assert
    assertNotNull(result, "Result should not be null");

    // Verify LoadHistory was created with RUNNING status
    ArgumentCaptor<LoadHistory> historyCaptor = ArgumentCaptor.forClass(LoadHistory.class);
    verify(loadHistoryRepository, atLeastOnce()).save(historyCaptor.capture());

    LoadHistory capturedHistory = historyCaptor.getAllValues().get(0);
    assertEquals("TEST_LOADER", capturedHistory.getLoaderCode());
    assertEquals("test-replica", capturedHistory.getReplicaName());
    assertNotNull(capturedHistory.getStartTime());

    // Verify loader status was updated
    ArgumentCaptor<Loader> loaderCaptor = ArgumentCaptor.forClass(Loader.class);
    verify(loaderRepository, atLeastOnce()).save(loaderCaptor.capture());

    // Should have been set to RUNNING, then back to IDLE
    assertTrue(loaderCaptor.getAllValues().stream()
        .anyMatch(l -> l.getLoadStatus() == LoadStatus.RUNNING));
    assertTrue(loaderCaptor.getAllValues().stream()
        .anyMatch(l -> l.getLoadStatus() == LoadStatus.IDLE));
  }

  @Test
  void testExecuteLoader_RecordsReplicaName() {
    // Act
    executorService.executeLoader(testLoader);

    // Assert
    ArgumentCaptor<LoadHistory> captor = ArgumentCaptor.forClass(LoadHistory.class);
    verify(loadHistoryRepository, atLeastOnce()).save(captor.capture());

    assertEquals("test-replica", captor.getValue().getReplicaName(),
        "Replica name should be recorded in history");
  }

  @Test
  void testExecuteLoader_UpdatesLoaderTimestamp() {
    // Arrange
    Instant beforeExecution = Instant.now();

    // Act
    executorService.executeLoader(testLoader);

    // Assert
    ArgumentCaptor<Loader> captor = ArgumentCaptor.forClass(Loader.class);
    verify(loaderRepository, atLeastOnce()).save(captor.capture());

    Loader finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);
    assertNotNull(finalState.getLastLoadTimestamp(),
        "Last load timestamp should be updated");
    assertTrue(finalState.getLastLoadTimestamp().isAfter(beforeExecution) ||
               finalState.getLastLoadTimestamp().equals(beforeExecution),
        "Timestamp should be recent");
  }

  @Test
  void testExecuteLoader_ClearsFailedSince() {
    // Arrange
    testLoader.setLoadStatus(LoadStatus.FAILED);
    testLoader.setFailedSince(Instant.now().minusSeconds(3600));

    // Act
    executorService.executeLoader(testLoader);

    // Assert
    ArgumentCaptor<Loader> captor = ArgumentCaptor.forClass(Loader.class);
    verify(loaderRepository, atLeastOnce()).save(captor.capture());

    Loader finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);
    assertNull(finalState.getFailedSince(),
        "Failed since should be cleared on successful execution");
    assertEquals(LoadStatus.IDLE, finalState.getLoadStatus());
  }

  @Test
  void testExecuteLoader_NullLoader_ThrowsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> executorService.executeLoader((Loader) null),
        "Should throw exception for null loader");
  }

  @Test
  void testExecuteLoader_ByCode_Success() {
    // Arrange
    when(loaderRepository.findByLoaderCode("TEST_LOADER"))
        .thenReturn(Optional.of(testLoader));
    when(loadHistoryRepository.save(any(LoadHistory.class))).thenReturn(testHistory);

    // Act
    LoadHistory result = executorService.executeLoader("TEST_LOADER");

    // Assert
    assertNotNull(result);
    verify(loaderRepository).findByLoaderCode("TEST_LOADER");
  }

  @Test
  void testExecuteLoader_ByCode_NotFound_ThrowsException() {
    // Arrange
    when(loaderRepository.findByLoaderCode("NONEXISTENT"))
        .thenReturn(Optional.empty());

    // Act & Assert
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> executorService.executeLoader("NONEXISTENT")
    );

    assertTrue(exception.getMessage().contains("Loader not found"));
  }

  @Test
  void testExecuteLoader_ExceptionHandling_RecordsFailure() {
    // Arrange - simulate an exception during execution
    when(loadHistoryRepository.save(any(LoadHistory.class)))
        .thenReturn(testHistory)  // First call succeeds (initial RUNNING record)
        .thenThrow(new RuntimeException("Database error"))  // Second call fails
        .thenReturn(testHistory);  // Third call succeeds (failure record)

    // Act
    LoadHistory result = executorService.executeLoader(testLoader);

    // Assert - should handle exception gracefully
    assertNotNull(result);

    // Verify loader status was set to FAILED
    ArgumentCaptor<Loader> loaderCaptor = ArgumentCaptor.forClass(Loader.class);
    verify(loaderRepository, atLeastOnce()).save(loaderCaptor.capture());

    Loader finalState = loaderCaptor.getAllValues().get(loaderCaptor.getAllValues().size() - 1);
    assertEquals(LoadStatus.FAILED, finalState.getLoadStatus());
    assertNotNull(finalState.getFailedSince(), "Failed since should be set");
  }

  @Test
  void testExecuteLoader_RecordsDuration() {
    // Act
    executorService.executeLoader(testLoader);

    // Assert
    ArgumentCaptor<LoadHistory> captor = ArgumentCaptor.forClass(LoadHistory.class);
    verify(loadHistoryRepository, atLeastOnce()).save(captor.capture());

    // Find the final saved history (with duration)
    LoadHistory finalHistory = captor.getAllValues().stream()
        .filter(h -> h.getDurationSeconds() != null)
        .findFirst()
        .orElse(null);

    assertNotNull(finalHistory, "Should have saved history with duration");
    assertTrue(finalHistory.getDurationSeconds() >= 0,
        "Duration should be non-negative");
  }

  @Test
  void testExecuteLoader_RecordsStartAndEndTime() {
    // Arrange
    Instant beforeExecution = Instant.now();

    // Act
    executorService.executeLoader(testLoader);

    // Assert
    ArgumentCaptor<LoadHistory> captor = ArgumentCaptor.forClass(LoadHistory.class);
    verify(loadHistoryRepository, atLeastOnce()).save(captor.capture());

    LoadHistory history = captor.getValue();
    assertNotNull(history.getStartTime(), "Start time should be recorded");
    assertTrue(history.getStartTime().isAfter(beforeExecution) ||
               history.getStartTime().equals(beforeExecution));
  }

  @Test
  void testExecuteLoader_StubReturnsZeroRecords() {
    // Act
    executorService.executeLoader(testLoader);

    // Assert
    ArgumentCaptor<LoadHistory> captor = ArgumentCaptor.forClass(LoadHistory.class);
    verify(loadHistoryRepository, atLeastOnce()).save(captor.capture());

    // Find final history with records loaded
    LoadHistory finalHistory = captor.getAllValues().stream()
        .filter(h -> h.getRecordsLoaded() != null)
        .findFirst()
        .orElse(null);

    assertNotNull(finalHistory);
    assertEquals(0L, finalHistory.getRecordsLoaded(),
        "Stub should return 0 records loaded");
    assertEquals(0L, finalHistory.getRecordsIngested(),
        "Stub should return 0 records ingested");
  }

  @Test
  void testExecuteLoader_SetsQueryTimeWindow() {
    // Act
    executorService.executeLoader(testLoader);

    // Assert
    ArgumentCaptor<LoadHistory> captor = ArgumentCaptor.forClass(LoadHistory.class);
    verify(loadHistoryRepository, atLeastOnce()).save(captor.capture());

    LoadHistory finalHistory = captor.getAllValues().stream()
        .filter(h -> h.getQueryFromTime() != null)
        .findFirst()
        .orElse(null);

    assertNotNull(finalHistory);
    assertNotNull(finalHistory.getQueryFromTime(),
        "Query from time should be set");
    assertNotNull(finalHistory.getQueryToTime(),
        "Query to time should be set");
    assertTrue(finalHistory.getQueryToTime().isAfter(finalHistory.getQueryFromTime()),
        "Query to time should be after from time");
  }

  @Test
  void testExecuteLoader_MultipleExecutions_Independent() {
    // Arrange
    when(loadHistoryRepository.save(any(LoadHistory.class))).thenReturn(testHistory);

    // Act
    LoadHistory result1 = executorService.executeLoader(testLoader);
    LoadHistory result2 = executorService.executeLoader(testLoader);

    // Assert
    assertNotNull(result1);
    assertNotNull(result2);

    // Should have created 2 separate history records
    verify(loadHistoryRepository, atLeast(2)).save(any(LoadHistory.class));
  }

  @Test
  void testExecuteLoader_LoaderStatusTransitions() {
    // Act
    executorService.executeLoader(testLoader);

    // Assert
    ArgumentCaptor<Loader> captor = ArgumentCaptor.forClass(Loader.class);
    verify(loaderRepository, atLeastOnce()).save(captor.capture());

    // Verify status transitions: IDLE → RUNNING → IDLE
    var savedLoaders = captor.getAllValues();
    assertTrue(savedLoaders.size() >= 2,
        "Should have saved loader at least twice");

    // At some point, loader should be RUNNING
    assertTrue(savedLoaders.stream()
        .anyMatch(l -> l.getLoadStatus() == LoadStatus.RUNNING),
        "Loader should transition to RUNNING");

    // Final state should be IDLE (success) or FAILED
    LoadStatus finalStatus = savedLoaders.get(savedLoaders.size() - 1).getLoadStatus();
    assertTrue(finalStatus == LoadStatus.IDLE || finalStatus == LoadStatus.FAILED,
        "Final status should be IDLE or FAILED");
  }

  @Test
  void testExecuteLoader_ReplicaNameFromProvider() {
    // Arrange
    when(replicaNameProvider.getReplicaName()).thenReturn("custom-replica-123");

    // Act
    executorService.executeLoader(testLoader);

    // Assert
    verify(replicaNameProvider, atLeastOnce()).getReplicaName();

    ArgumentCaptor<LoadHistory> captor = ArgumentCaptor.forClass(LoadHistory.class);
    verify(loadHistoryRepository, atLeastOnce()).save(captor.capture());

    assertEquals("custom-replica-123", captor.getValue().getReplicaName());
  }
}
