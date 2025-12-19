package com.tiqmo.monitoring.loader.service.signals;

import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import com.tiqmo.monitoring.loader.domain.signals.repo.SignalsHistoryRepository;
import com.tiqmo.monitoring.loader.dto.common.ErrorCode;
import com.tiqmo.monitoring.loader.dto.signals.BulkSignalsRequest;
import com.tiqmo.monitoring.loader.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SignalsIngestService.
 *
 * <p>Tests cover:
 * - Single signal append
 * - Bulk signal append
 * - Validation logic
 * - Error handling
 * - CreateTime auto-setting
 *
 * @author Hassan Rawashdeh (Claude Code)
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class SignalsIngestServiceTest {

  @Mock
  private SignalsHistoryRepository repository;

  @InjectMocks
  private SignalsIngestService service;

  private SignalsHistory validSignal;

  @BeforeEach
  void setUp() {
    validSignal = SignalsHistory.builder()
        .loaderCode("TEST_LOADER")
        .loadTimeStamp(1706353200L)
        .segmentCode("1")
        .recCount(100L)
        .maxVal(95.5)
        .minVal(10.2)
        .avgVal(52.8)
        .sumVal(5280.0)
        .build();
  }

  // ===================================================================================
  // Test: append() - Success Cases
  // ===================================================================================

  @Test
  void testAppend_Success_WithCreateTime() {
    // Arrange
    validSignal.setCreateTime(1706353300L);
    when(repository.save(any(SignalsHistory.class))).thenReturn(validSignal);

    // Act
    SignalsHistory result = service.append(validSignal);

    // Assert
    assertNotNull(result);
    assertEquals("TEST_LOADER", result.getLoaderCode());
    assertEquals(1706353300L, result.getCreateTime(), "CreateTime should remain unchanged");
    verify(repository).save(validSignal);
  }

  @Test
  void testAppend_Success_WithoutCreateTime_AutoSetsTimestamp() {
    // Arrange
    validSignal.setCreateTime(null);
    when(repository.save(any(SignalsHistory.class))).thenAnswer(i -> i.getArgument(0));

    // Act
    long beforeExecution = System.currentTimeMillis() / 1000;
    SignalsHistory result = service.append(validSignal);
    long afterExecution = System.currentTimeMillis() / 1000;

    // Assert
    assertNotNull(result.getCreateTime(), "CreateTime should be auto-set");
    assertTrue(result.getCreateTime() >= beforeExecution && result.getCreateTime() <= afterExecution,
        "CreateTime should be current timestamp");

    // Verify saved entity has createTime
    ArgumentCaptor<SignalsHistory> captor = ArgumentCaptor.forClass(SignalsHistory.class);
    verify(repository).save(captor.capture());
    assertNotNull(captor.getValue().getCreateTime());
  }

  @Test
  void testAppend_Success_SavesCorrectData() {
    // Arrange
    when(repository.save(any(SignalsHistory.class))).thenAnswer(i -> {
      SignalsHistory saved = i.getArgument(0);
      saved.setId(123L);
      return saved;
    });

    // Act
    SignalsHistory result = service.append(validSignal);

    // Assert
    assertNotNull(result.getId());
    assertEquals(123L, result.getId());
    assertEquals("TEST_LOADER", result.getLoaderCode());
    assertEquals(1706353200L, result.getLoadTimeStamp());
    assertEquals("1", result.getSegmentCode());
    assertEquals(100L, result.getRecCount());
  }

  // ===================================================================================
  // Test: append() - Validation Failures
  // ===================================================================================

  @Test
  void testAppend_NullLoaderCode_ThrowsException() {
    // Arrange
    validSignal.setLoaderCode(null);

    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.append(validSignal));

    assertEquals(ErrorCode.VALIDATION_REQUIRED_FIELD, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("Loader code is required"));
    verify(repository, never()).save(any());
  }

  @Test
  void testAppend_BlankLoaderCode_ThrowsException() {
    // Arrange
    validSignal.setLoaderCode("   ");

    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.append(validSignal));

    assertEquals(ErrorCode.VALIDATION_REQUIRED_FIELD, exception.getErrorCode());
    verify(repository, never()).save(any());
  }

  @Test
  void testAppend_NullTimestamp_ThrowsException() {
    // Arrange
    validSignal.setLoadTimeStamp(null);

    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.append(validSignal));

    assertEquals(ErrorCode.SIGNAL_INVALID_TIMESTAMP, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("Load timestamp is required"));
    verify(repository, never()).save(any());
  }

  // ===================================================================================
  // Test: bulkAppend() - Success Cases
  // ===================================================================================

  @Test
  void testBulkAppend_Success_MultipleSignals() {
    // Arrange
    List<BulkSignalsRequest.SignalData> signalDataList = List.of(
        createSignalData(1706353200L, "1", 100L),
        createSignalData(1706353300L, "2", 200L),
        createSignalData(1706353400L, "3", 300L)
    );

    when(repository.saveAll(anyList())).thenAnswer(i -> {
      List<SignalsHistory> signals = i.getArgument(0);
      signals.forEach(s -> s.setId(999L));
      return signals;
    });

    // Act
    List<SignalsHistory> results = service.bulkAppend("TEST_LOADER", signalDataList);

    // Assert
    assertEquals(3, results.size());
    assertEquals("TEST_LOADER", results.get(0).getLoaderCode());
    assertEquals("TEST_LOADER", results.get(1).getLoaderCode());
    assertEquals("TEST_LOADER", results.get(2).getLoaderCode());

    verify(repository).saveAll(anyList());
  }

  @Test
  void testBulkAppend_Success_SetsCreateTimeForAll() {
    // Arrange
    List<BulkSignalsRequest.SignalData> signalDataList = List.of(
        createSignalData(1706353200L, "1", 100L),
        createSignalData(1706353300L, "2", 200L)
    );

    when(repository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

    // Act
    long beforeExecution = System.currentTimeMillis() / 1000;
    List<SignalsHistory> results = service.bulkAppend("TEST_LOADER", signalDataList);
    long afterExecution = System.currentTimeMillis() / 1000;

    // Assert
    assertEquals(2, results.size());

    results.forEach(signal -> {
      assertNotNull(signal.getCreateTime(), "CreateTime should be set");
      assertTrue(signal.getCreateTime() >= beforeExecution && signal.getCreateTime() <= afterExecution,
          "CreateTime should be current timestamp");
    });

    // Verify all signals saved together
    ArgumentCaptor<List<SignalsHistory>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveAll(captor.capture());
    assertEquals(2, captor.getValue().size());
  }

  @Test
  void testBulkAppend_Success_PreservesExistingCreateTime() {
    // Arrange - one signal has createTime already set
    BulkSignalsRequest.SignalData data1 = createSignalData(1706353200L, "1", 100L);
    BulkSignalsRequest.SignalData data2 = createSignalData(1706353300L, "2", 200L);

    List<BulkSignalsRequest.SignalData> signalDataList = List.of(data1, data2);

    when(repository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

    // Act
    service.bulkAppend("TEST_LOADER", signalDataList);

    // Assert
    ArgumentCaptor<List<SignalsHistory>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveAll(captor.capture());

    List<SignalsHistory> savedSignals = captor.getValue();
    assertEquals(2, savedSignals.size());
    savedSignals.forEach(signal -> assertNotNull(signal.getCreateTime()));
  }

  @Test
  void testBulkAppend_Success_SingleSignal() {
    // Arrange
    List<BulkSignalsRequest.SignalData> signalDataList = List.of(
        createSignalData(1706353200L, "1", 100L)
    );

    when(repository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

    // Act
    List<SignalsHistory> results = service.bulkAppend("TEST_LOADER", signalDataList);

    // Assert
    assertEquals(1, results.size());
    assertEquals("TEST_LOADER", results.get(0).getLoaderCode());
    verify(repository).saveAll(anyList());
  }

  // ===================================================================================
  // Test: bulkAppend() - Validation Failures
  // ===================================================================================

  @Test
  void testBulkAppend_NullLoaderCode_ThrowsException() {
    // Arrange
    List<BulkSignalsRequest.SignalData> signalDataList = List.of(
        createSignalData(1706353200L, "1", 100L)
    );

    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.bulkAppend(null, signalDataList));

    assertEquals(ErrorCode.VALIDATION_REQUIRED_FIELD, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("Loader code is required"));
    verify(repository, never()).saveAll(anyList());
  }

  @Test
  void testBulkAppend_BlankLoaderCode_ThrowsException() {
    // Arrange
    List<BulkSignalsRequest.SignalData> signalDataList = List.of(
        createSignalData(1706353200L, "1", 100L)
    );

    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.bulkAppend("   ", signalDataList));

    assertEquals(ErrorCode.VALIDATION_REQUIRED_FIELD, exception.getErrorCode());
    verify(repository, never()).saveAll(anyList());
  }

  @Test
  void testBulkAppend_NullSignalDataList_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.bulkAppend("TEST_LOADER", null));

    assertEquals(ErrorCode.VALIDATION_REQUIRED_FIELD, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("Signal data list is required"));
    verify(repository, never()).saveAll(anyList());
  }

  @Test
  void testBulkAppend_EmptySignalDataList_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.bulkAppend("TEST_LOADER", new ArrayList<>()));

    assertEquals(ErrorCode.VALIDATION_REQUIRED_FIELD, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("cannot be empty"));
    verify(repository, never()).saveAll(anyList());
  }

  // ===================================================================================
  // Test: Edge Cases
  // ===================================================================================

  @Test
  void testAppend_AllOptionalFieldsNull_Success() {
    // Arrange - only required fields set
    SignalsHistory minimalSignal = SignalsHistory.builder()
        .loaderCode("TEST_LOADER")
        .loadTimeStamp(1706353200L)
        .segmentCode("1")
        .build();

    when(repository.save(any(SignalsHistory.class))).thenAnswer(i -> i.getArgument(0));

    // Act
    SignalsHistory result = service.append(minimalSignal);

    // Assert
    assertNotNull(result);
    assertEquals("TEST_LOADER", result.getLoaderCode());
    assertNull(result.getRecCount());
    assertNull(result.getMaxVal());
    assertNull(result.getMinVal());
  }

  @Test
  void testBulkAppend_LargeDataset_Success() {
    // Arrange - 1000 signals
    List<BulkSignalsRequest.SignalData> largeDataset = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      largeDataset.add(createSignalData(1706353200L + i, String.valueOf(i % 10), (long) i));
    }

    when(repository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

    // Act
    List<SignalsHistory> results = service.bulkAppend("TEST_LOADER", largeDataset);

    // Assert
    assertEquals(1000, results.size());
    verify(repository).saveAll(anyList());
  }

  @Test
  void testAppend_RepositoryThrowsException_PropagatesException() {
    // Arrange
    when(repository.save(any(SignalsHistory.class)))
        .thenThrow(new RuntimeException("Database connection failed"));

    // Act & Assert
    assertThrows(RuntimeException.class, () -> service.append(validSignal));
  }

  @Test
  void testBulkAppend_RepositoryThrowsException_PropagatesException() {
    // Arrange
    List<BulkSignalsRequest.SignalData> signalDataList = List.of(
        createSignalData(1706353200L, "1", 100L)
    );

    when(repository.saveAll(anyList()))
        .thenThrow(new RuntimeException("Database connection failed"));

    // Act & Assert
    assertThrows(RuntimeException.class,
        () -> service.bulkAppend("TEST_LOADER", signalDataList));
  }

  // ===================================================================================
  // Helper Methods
  // ===================================================================================

  private BulkSignalsRequest.SignalData createSignalData(Long timestamp, String segmentCode, Long recCount) {
    BulkSignalsRequest.SignalData data = new BulkSignalsRequest.SignalData();
    data.setLoadTimeStamp(timestamp);
    data.setSegmentCode(segmentCode);
    data.setRecCount(recCount);
    data.setMaxVal(95.5);
    data.setMinVal(10.2);
    data.setAvgVal(52.8);
    data.setSumVal(5280.0);
    return data;
  }
}
