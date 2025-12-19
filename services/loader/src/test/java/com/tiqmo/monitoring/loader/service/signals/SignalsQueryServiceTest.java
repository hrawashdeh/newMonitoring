package com.tiqmo.monitoring.loader.service.signals;

import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import com.tiqmo.monitoring.loader.domain.signals.repo.SignalsHistoryRepository;
import com.tiqmo.monitoring.loader.dto.common.ErrorCode;
import com.tiqmo.monitoring.loader.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SignalsQueryService.
 *
 * <p>Tests cover:
 * - Query by loader and time range
 * - Query by loader, segment, and time range
 * - Validation logic (loader code, segment code, time range)
 * - Error handling
 * - Empty results
 *
 * @author Hassan Rawashdeh (Claude Code)
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class SignalsQueryServiceTest {

  @Mock
  private SignalsHistoryRepository repository;

  @InjectMocks
  private SignalsQueryService service;

  private List<SignalsHistory> sampleSignals;

  @BeforeEach
  void setUp() {
    sampleSignals = createSampleSignals();
  }

  // ===================================================================================
  // Test: byLoaderBetween() - Success Cases
  // ===================================================================================

  @Test
  void testByLoaderBetween_Success_ReturnsResults() {
    // Arrange
    when(repository.findByLoaderCodeAndLoadTimeStampBetween("TEST_LOADER", 1000L, 2000L))
        .thenReturn(sampleSignals);

    // Act
    List<SignalsHistory> results = service.byLoaderBetween("TEST_LOADER", 1000L, 2000L);

    // Assert
    assertNotNull(results);
    assertEquals(3, results.size());
    assertEquals("TEST_LOADER", results.get(0).getLoaderCode());
    verify(repository).findByLoaderCodeAndLoadTimeStampBetween("TEST_LOADER", 1000L, 2000L);
  }

  @Test
  void testByLoaderBetween_Success_EmptyResults() {
    // Arrange
    when(repository.findByLoaderCodeAndLoadTimeStampBetween("TEST_LOADER", 1000L, 2000L))
        .thenReturn(List.of());

    // Act
    List<SignalsHistory> results = service.byLoaderBetween("TEST_LOADER", 1000L, 2000L);

    // Assert
    assertNotNull(results);
    assertTrue(results.isEmpty());
    verify(repository).findByLoaderCodeAndLoadTimeStampBetween("TEST_LOADER", 1000L, 2000L);
  }

  @Test
  void testByLoaderBetween_Success_LargeTimeRange() {
    // Arrange - 1 year time range
    long fromEpoch = 1609459200L; // 2021-01-01
    long toEpoch = 1640995200L;   // 2022-01-01
    when(repository.findByLoaderCodeAndLoadTimeStampBetween("TEST_LOADER", fromEpoch, toEpoch))
        .thenReturn(sampleSignals);

    // Act
    List<SignalsHistory> results = service.byLoaderBetween("TEST_LOADER", fromEpoch, toEpoch);

    // Assert
    assertNotNull(results);
    assertEquals(3, results.size());
  }

  @Test
  void testByLoaderBetween_Success_SingleSecondRange() {
    // Arrange - minimal valid time range (1 second)
    when(repository.findByLoaderCodeAndLoadTimeStampBetween("TEST_LOADER", 1000L, 1001L))
        .thenReturn(List.of(sampleSignals.get(0)));

    // Act
    List<SignalsHistory> results = service.byLoaderBetween("TEST_LOADER", 1000L, 1001L);

    // Assert
    assertNotNull(results);
    assertEquals(1, results.size());
  }

  // ===================================================================================
  // Test: byLoaderBetween() - Validation Failures
  // ===================================================================================

  @Test
  void testByLoaderBetween_NullLoaderCode_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderBetween(null, 1000L, 2000L));

    assertEquals(ErrorCode.VALIDATION_REQUIRED_FIELD, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("Loader code is required"));
    verify(repository, never()).findByLoaderCodeAndLoadTimeStampBetween(any(), anyLong(), anyLong());
  }

  @Test
  void testByLoaderBetween_BlankLoaderCode_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderBetween("   ", 1000L, 2000L));

    assertEquals(ErrorCode.VALIDATION_REQUIRED_FIELD, exception.getErrorCode());
    verify(repository, never()).findByLoaderCodeAndLoadTimeStampBetween(any(), anyLong(), anyLong());
  }

  @Test
  void testByLoaderBetween_NegativeFromEpoch_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderBetween("TEST_LOADER", -100L, 2000L));

    assertEquals(ErrorCode.VALIDATION_INVALID_VALUE, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("From time cannot be negative"));
    verify(repository, never()).findByLoaderCodeAndLoadTimeStampBetween(any(), anyLong(), anyLong());
  }

  @Test
  void testByLoaderBetween_NegativeToEpoch_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderBetween("TEST_LOADER", 1000L, -100L));

    assertEquals(ErrorCode.VALIDATION_INVALID_VALUE, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("To time cannot be negative"));
    verify(repository, never()).findByLoaderCodeAndLoadTimeStampBetween(any(), anyLong(), anyLong());
  }

  @Test
  void testByLoaderBetween_FromEpochGreaterThanToEpoch_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderBetween("TEST_LOADER", 2000L, 1000L));

    assertEquals(ErrorCode.VALIDATION_INVALID_VALUE, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("From time must be before to time"));
    verify(repository, never()).findByLoaderCodeAndLoadTimeStampBetween(any(), anyLong(), anyLong());
  }

  @Test
  void testByLoaderBetween_FromEpochEqualsToEpoch_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderBetween("TEST_LOADER", 1000L, 1000L));

    assertEquals(ErrorCode.VALIDATION_INVALID_VALUE, exception.getErrorCode());
    verify(repository, never()).findByLoaderCodeAndLoadTimeStampBetween(any(), anyLong(), anyLong());
  }

  // ===================================================================================
  // Test: byLoaderAndSegmentBetween() - Success Cases
  // ===================================================================================

  @Test
  void testByLoaderAndSegmentBetween_Success_ReturnsResults() {
    // Arrange
    when(repository.findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        "TEST_LOADER", "SEG_001", 1000L, 2000L))
        .thenReturn(List.of(sampleSignals.get(0)));

    // Act
    List<SignalsHistory> results = service.byLoaderAndSegmentBetween(
        "TEST_LOADER", "SEG_001", 1000L, 2000L);

    // Assert
    assertNotNull(results);
    assertEquals(1, results.size());
    assertEquals("TEST_LOADER", results.get(0).getLoaderCode());
    assertEquals("1", results.get(0).getSegmentCode());
    verify(repository).findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        "TEST_LOADER", "SEG_001", 1000L, 2000L);
  }

  @Test
  void testByLoaderAndSegmentBetween_Success_EmptyResults() {
    // Arrange
    when(repository.findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        "TEST_LOADER", "SEG_999", 1000L, 2000L))
        .thenReturn(List.of());

    // Act
    List<SignalsHistory> results = service.byLoaderAndSegmentBetween(
        "TEST_LOADER", "SEG_999", 1000L, 2000L);

    // Assert
    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  @Test
  void testByLoaderAndSegmentBetween_Success_MultipleResults() {
    // Arrange
    List<SignalsHistory> segmentSignals = List.of(sampleSignals.get(0), sampleSignals.get(1));
    when(repository.findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        "TEST_LOADER", "SEG_001", 1000L, 2000L))
        .thenReturn(segmentSignals);

    // Act
    List<SignalsHistory> results = service.byLoaderAndSegmentBetween(
        "TEST_LOADER", "SEG_001", 1000L, 2000L);

    // Assert
    assertEquals(2, results.size());
  }

  // ===================================================================================
  // Test: byLoaderAndSegmentBetween() - Validation Failures
  // ===================================================================================

  @Test
  void testByLoaderAndSegmentBetween_NullLoaderCode_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderAndSegmentBetween(null, "SEG_001", 1000L, 2000L));

    assertEquals(ErrorCode.VALIDATION_REQUIRED_FIELD, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("Loader code is required"));
    verify(repository, never()).findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        any(), any(), anyLong(), anyLong());
  }

  @Test
  void testByLoaderAndSegmentBetween_BlankLoaderCode_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderAndSegmentBetween("   ", "SEG_001", 1000L, 2000L));

    assertEquals(ErrorCode.VALIDATION_REQUIRED_FIELD, exception.getErrorCode());
    verify(repository, never()).findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        any(), any(), anyLong(), anyLong());
  }

  @Test
  void testByLoaderAndSegmentBetween_NullSegmentCode_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderAndSegmentBetween("TEST_LOADER", null, 1000L, 2000L));

    assertEquals(ErrorCode.VALIDATION_REQUIRED_FIELD, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("Segment code is required"));
    verify(repository, never()).findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        any(), any(), anyLong(), anyLong());
  }

  @Test
  void testByLoaderAndSegmentBetween_BlankSegmentCode_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderAndSegmentBetween("TEST_LOADER", "   ", 1000L, 2000L));

    assertEquals(ErrorCode.VALIDATION_REQUIRED_FIELD, exception.getErrorCode());
    verify(repository, never()).findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        any(), any(), anyLong(), anyLong());
  }

  @Test
  void testByLoaderAndSegmentBetween_NegativeFromEpoch_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderAndSegmentBetween("TEST_LOADER", "SEG_001", -100L, 2000L));

    assertEquals(ErrorCode.VALIDATION_INVALID_VALUE, exception.getErrorCode());
    verify(repository, never()).findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        any(), any(), anyLong(), anyLong());
  }

  @Test
  void testByLoaderAndSegmentBetween_NegativeToEpoch_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderAndSegmentBetween("TEST_LOADER", "SEG_001", 1000L, -100L));

    assertEquals(ErrorCode.VALIDATION_INVALID_VALUE, exception.getErrorCode());
    verify(repository, never()).findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        any(), any(), anyLong(), anyLong());
  }

  @Test
  void testByLoaderAndSegmentBetween_FromEpochGreaterThanToEpoch_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderAndSegmentBetween("TEST_LOADER", "SEG_001", 2000L, 1000L));

    assertEquals(ErrorCode.VALIDATION_INVALID_VALUE, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("From time must be before to time"));
    verify(repository, never()).findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        any(), any(), anyLong(), anyLong());
  }

  @Test
  void testByLoaderAndSegmentBetween_FromEpochEqualsToEpoch_ThrowsException() {
    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.byLoaderAndSegmentBetween("TEST_LOADER", "SEG_001", 1000L, 1000L));

    assertEquals(ErrorCode.VALIDATION_INVALID_VALUE, exception.getErrorCode());
    verify(repository, never()).findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        any(), any(), anyLong(), anyLong());
  }

  // ===================================================================================
  // Test: Edge Cases & Repository Errors
  // ===================================================================================

  @Test
  void testByLoaderBetween_RepositoryThrowsException_PropagatesException() {
    // Arrange
    when(repository.findByLoaderCodeAndLoadTimeStampBetween("TEST_LOADER", 1000L, 2000L))
        .thenThrow(new RuntimeException("Database connection failed"));

    // Act & Assert
    assertThrows(RuntimeException.class,
        () -> service.byLoaderBetween("TEST_LOADER", 1000L, 2000L));
  }

  @Test
  void testByLoaderAndSegmentBetween_RepositoryThrowsException_PropagatesException() {
    // Arrange
    when(repository.findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        "TEST_LOADER", "SEG_001", 1000L, 2000L))
        .thenThrow(new RuntimeException("Database connection failed"));

    // Act & Assert
    assertThrows(RuntimeException.class,
        () -> service.byLoaderAndSegmentBetween("TEST_LOADER", "SEG_001", 1000L, 2000L));
  }

  @Test
  void testByLoaderBetween_ZeroEpochTimes_ValidRange() {
    // Arrange - from epoch 0 to epoch 100 (valid range starting from Unix epoch)
    when(repository.findByLoaderCodeAndLoadTimeStampBetween("TEST_LOADER", 0L, 100L))
        .thenReturn(List.of());

    // Act
    List<SignalsHistory> results = service.byLoaderBetween("TEST_LOADER", 0L, 100L);

    // Assert
    assertNotNull(results);
    verify(repository).findByLoaderCodeAndLoadTimeStampBetween("TEST_LOADER", 0L, 100L);
  }

  @Test
  void testByLoaderBetween_LargeDataset_Success() {
    // Arrange - simulate large result set (1000 signals)
    List<SignalsHistory> largeDataset = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      SignalsHistory signal = SignalsHistory.builder()
          .loaderCode("TEST_LOADER")
          .loadTimeStamp(1000L + i)
          .segmentCode(String.valueOf(i % 10))
          .recCount((long) i)
          .build();
      largeDataset.add(signal);
    }

    when(repository.findByLoaderCodeAndLoadTimeStampBetween("TEST_LOADER", 1000L, 2000L))
        .thenReturn(largeDataset);

    // Act
    List<SignalsHistory> results = service.byLoaderBetween("TEST_LOADER", 1000L, 2000L);

    // Assert
    assertEquals(1000, results.size());
  }

  @Test
  void testByLoaderAndSegmentBetween_SpecialCharactersInCodes_Success() {
    // Arrange - test with special characters in codes
    when(repository.findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        "LOADER-WITH-DASHES", "SEG_001_V2", 1000L, 2000L))
        .thenReturn(List.of());

    // Act
    List<SignalsHistory> results = service.byLoaderAndSegmentBetween(
        "LOADER-WITH-DASHES", "SEG_001_V2", 1000L, 2000L);

    // Assert
    assertNotNull(results);
    verify(repository).findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
        "LOADER-WITH-DASHES", "SEG_001_V2", 1000L, 2000L);
  }

  // ===================================================================================
  // Helper Methods
  // ===================================================================================

  private List<SignalsHistory> createSampleSignals() {
    SignalsHistory signal1 = SignalsHistory.builder()
        .id(1L)
        .loaderCode("TEST_LOADER")
        .loadTimeStamp(1100L)
        .segmentCode("1")
        .recCount(100L)
        .maxVal(95.5)
        .minVal(10.2)
        .avgVal(52.8)
        .sumVal(5280.0)
        .createTime(1706353300L)
        .build();

    SignalsHistory signal2 = SignalsHistory.builder()
        .id(2L)
        .loaderCode("TEST_LOADER")
        .loadTimeStamp(1500L)
        .segmentCode("2")
        .recCount(200L)
        .maxVal(85.0)
        .minVal(15.5)
        .avgVal(50.2)
        .sumVal(10040.0)
        .createTime(1706353400L)
        .build();

    SignalsHistory signal3 = SignalsHistory.builder()
        .id(3L)
        .loaderCode("TEST_LOADER")
        .loadTimeStamp(1900L)
        .segmentCode("3")
        .recCount(150L)
        .maxVal(99.9)
        .minVal(5.0)
        .avgVal(55.5)
        .sumVal(8325.0)
        .createTime(1706353500L)
        .build();

    return List.of(signal1, signal2, signal3);
  }
}
