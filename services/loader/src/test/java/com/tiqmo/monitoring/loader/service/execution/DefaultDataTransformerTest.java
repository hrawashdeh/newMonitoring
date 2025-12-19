package com.tiqmo.monitoring.loader.service.execution;

import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import com.tiqmo.monitoring.loader.service.signals.SegmentCombinationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DefaultDataTransformer.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class DefaultDataTransformerTest {

  @Mock
  private SegmentCombinationService segmentCombinationService;

  private DataTransformer transformer;
  private LoaderQueryResult testResult;

  @BeforeEach
  void setUp() {
    // Mock segment combination service to return segment code 1 for any combination (including nulls)
    when(segmentCombinationService.getOrCreateSegmentCode(
        any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any()
    )).thenReturn(1L);

    transformer = new DefaultDataTransformer(segmentCombinationService);
  }

  @Test
  void testTransform_Success_AllFields() {
    // Arrange
    List<Map<String, Object>> rows = List.of(
        Map.of(
            "timestamp", 1706353200L,
            "segment_1", "SEG_001",
            "segment_2", "SEG_002",
            "rec_count", 100L,
            "max_val", 95.5,
            "min_val", 10.2,
            "avg_val", 52.8,
            "sum_val", 5280.0
        )
    );

    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        rows,
        rows.size()
    );

    // Act
    List<SignalsHistory> results = transformer.transform("TEST_LOADER", testResult);

    // Assert
    assertEquals(1, results.size());
    SignalsHistory signal = results.get(0);
    assertEquals("TEST_LOADER", signal.getLoaderCode());
    assertEquals(1706353200L, signal.getLoadTimeStamp());
    assertEquals("1", signal.getSegmentCode()); // Mocked service returns 1L
    assertEquals(100L, signal.getRecCount());
    assertEquals(95.5, signal.getMaxVal());
    assertEquals(10.2, signal.getMinVal());
    assertEquals(52.8, signal.getAvgVal());
    assertEquals(5280.0, signal.getSumVal());
    assertNotNull(signal.getCreateTime());
  }

  @Test
  void testTransform_Success_OnlyRequiredField() {
    // Arrange - only timestamp provided
    List<Map<String, Object>> rows = List.of(
        Map.of("timestamp", 1706353200L)
    );

    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        rows,
        rows.size()
    );

    // Act
    List<SignalsHistory> results = transformer.transform("TEST_LOADER", testResult);

    // Assert
    assertEquals(1, results.size());
    SignalsHistory signal = results.get(0);
    assertEquals("TEST_LOADER", signal.getLoaderCode());
    assertEquals(1706353200L, signal.getLoadTimeStamp());
    assertEquals("1", signal.getSegmentCode()); // Mocked service returns 1L even with all null segments
    assertNull(signal.getRecCount());
    assertNull(signal.getMaxVal());
    assertNull(signal.getMinVal());
    assertNull(signal.getAvgVal());
    assertNull(signal.getSumVal());
  }

  @Test
  void testTransform_TimestampVariations() {
    // Arrange - different timestamp column names
    List<Map<String, Object>> rows = List.of(
        Map.of("timestamp", 1706353200L),
        Map.of("ts", 1706353300L),
        Map.of("time", 1706353400L),
        Map.of("load_time_stamp", 1706353500L)
    );

    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        rows,
        rows.size()
    );

    // Act
    List<SignalsHistory> results = transformer.transform("TEST_LOADER", testResult);

    // Assert
    assertEquals(4, results.size());
    assertEquals(1706353200L, results.get(0).getLoadTimeStamp());
    assertEquals(1706353300L, results.get(1).getLoadTimeStamp());
    assertEquals(1706353400L, results.get(2).getLoadTimeStamp());
    assertEquals(1706353500L, results.get(3).getLoadTimeStamp());
  }

  @Test
  void testTransform_TimestampMillis_AutoConverted() {
    // Arrange - timestamp in milliseconds (should be converted to seconds)
    List<Map<String, Object>> rows = List.of(
        Map.of("timestamp", 1706353200000L) // millis
    );

    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        rows,
        rows.size()
    );

    // Act
    List<SignalsHistory> results = transformer.transform("TEST_LOADER", testResult);

    // Assert
    assertEquals(1706353200L, results.get(0).getLoadTimeStamp(),
        "Should convert millis to seconds");
  }

  @Test
  void testTransform_TimestampInstant() {
    // Arrange - timestamp as Instant
    Instant timestamp = Instant.parse("2024-01-27T10:00:00Z");
    List<Map<String, Object>> rows = List.of(
        Map.of("timestamp", timestamp)
    );

    testResult = new LoaderQueryResult(
        timestamp,
        Instant.now(),
        rows,
        rows.size()
    );

    // Act
    List<SignalsHistory> results = transformer.transform("TEST_LOADER", testResult);

    // Assert
    assertEquals(timestamp.getEpochSecond(), results.get(0).getLoadTimeStamp());
  }

  @Test
  void testTransform_TimestampString_EpochSeconds() {
    // Arrange - timestamp as String (epoch seconds)
    List<Map<String, Object>> rows = List.of(
        Map.of("timestamp", "1706353200")
    );

    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        rows,
        rows.size()
    );

    // Act
    List<SignalsHistory> results = transformer.transform("TEST_LOADER", testResult);

    // Assert
    assertEquals(1706353200L, results.get(0).getLoadTimeStamp());
  }

  @Test
  void testTransform_TimestampString_ISO8601() {
    // Arrange - timestamp as String (ISO-8601)
    List<Map<String, Object>> rows = List.of(
        Map.of("timestamp", "2024-01-27T10:00:00Z")
    );

    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        rows,
        rows.size()
    );

    // Act
    List<SignalsHistory> results = transformer.transform("TEST_LOADER", testResult);

    // Assert
    assertEquals(1706349600L, results.get(0).getLoadTimeStamp());
  }

  @Test
  void testTransform_CaseInsensitiveColumnNames() {
    // Arrange - mixed case column names
    List<Map<String, Object>> rows = List.of(
        Map.of(
            "TIMESTAMP", 1706353200L,
            "Segment_1", "SEG_001",
            "REC_COUNT", 100L
        )
    );

    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        rows,
        rows.size()
    );

    // Act
    List<SignalsHistory> results = transformer.transform("TEST_LOADER", testResult);

    // Assert
    assertEquals(1, results.size());
    assertEquals(1706353200L, results.get(0).getLoadTimeStamp());
    assertEquals("1", results.get(0).getSegmentCode()); // Mocked service returns 1L
    assertEquals(100L, results.get(0).getRecCount());
  }

  @Test
  void testTransform_MultipleRows() {
    // Arrange
    List<Map<String, Object>> rows = List.of(
        Map.of("timestamp", 1706353200L, "rec_count", 100L),
        Map.of("timestamp", 1706353300L, "rec_count", 200L),
        Map.of("timestamp", 1706353400L, "rec_count", 300L)
    );

    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        rows,
        rows.size()
    );

    // Act
    List<SignalsHistory> results = transformer.transform("TEST_LOADER", testResult);

    // Assert
    assertEquals(3, results.size());
    assertEquals(100L, results.get(0).getRecCount());
    assertEquals(200L, results.get(1).getRecCount());
    assertEquals(300L, results.get(2).getRecCount());
  }

  @Test
  void testTransform_EmptyResult() {
    // Arrange
    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        List.of(),
        0
    );

    // Act
    List<SignalsHistory> results = transformer.transform("TEST_LOADER", testResult);

    // Assert
    assertTrue(results.isEmpty());
  }

  @Test
  void testTransform_MissingTimestamp_ThrowsException() {
    // Arrange - no timestamp field
    List<Map<String, Object>> rows = List.of(
        Map.of("segment_1", "SEG_001")
    );

    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        rows,
        rows.size()
    );

    // Act & Assert
    DataTransformer.TransformationException exception = assertThrows(
        DataTransformer.TransformationException.class,
        () -> transformer.transform("TEST_LOADER", testResult)
    );

    assertTrue(exception.getMessage().contains("Missing required field 'timestamp'"));
  }

  @Test
  void testTransform_NullLoaderCode_ThrowsException() {
    // Arrange
    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        List.of(),
        0
    );

    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> transformer.transform(null, testResult),
        "Should throw exception for null loader code");
  }

  @Test
  void testTransform_BlankLoaderCode_ThrowsException() {
    // Arrange
    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        List.of(),
        0
    );

    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> transformer.transform("   ", testResult),
        "Should throw exception for blank loader code");
  }

  @Test
  void testTransform_NullQueryResult_ThrowsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> transformer.transform("TEST_LOADER", null),
        "Should throw exception for null query result");
  }

  @Test
  void testTransform_TypeConversion_Integer() {
    // Arrange - Integer timestamp
    List<Map<String, Object>> rows = List.of(
        Map.of("timestamp", 1706353200)  // Integer
    );

    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        rows,
        rows.size()
    );

    // Act
    List<SignalsHistory> results = transformer.transform("TEST_LOADER", testResult);

    // Assert
    assertEquals(1706353200L, results.get(0).getLoadTimeStamp());
  }

  @Test
  void testTransform_TypeConversion_StringNumbers() {
    // Arrange - String numbers for optional fields
    List<Map<String, Object>> rows = List.of(
        Map.of(
            "timestamp", 1706353200L,
            "rec_count", "100",
            "max_val", "95.5",
            "min_val", "10.2"
        )
    );

    testResult = new LoaderQueryResult(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        rows,
        rows.size()
    );

    // Act
    List<SignalsHistory> results = transformer.transform("TEST_LOADER", testResult);

    // Assert
    assertEquals(100L, results.get(0).getRecCount());
    assertEquals(95.5, results.get(0).getMaxVal());
    assertEquals(10.2, results.get(0).getMinVal());
  }

  @Test
  void testLoadQueryResult_Validation() {
    // Test LoaderQueryResult validation
    Instant now = Instant.now();
    List<Map<String, Object>> rows = List.of();

    // Null queryFromTime
    assertThrows(IllegalArgumentException.class,
        () -> new LoaderQueryResult(null, now, rows, 0));

    // Null queryToTime
    assertThrows(IllegalArgumentException.class,
        () -> new LoaderQueryResult(now, null, rows, 0));

    // Null rows
    assertThrows(IllegalArgumentException.class,
        () -> new LoaderQueryResult(now, now, null, 0));

    // Negative rowCount
    assertThrows(IllegalArgumentException.class,
        () -> new LoaderQueryResult(now, now, rows, -1));
  }

  @Test
  void testLoadQueryResult_isEmpty() {
    Instant now = Instant.now();

    // Empty result
    LoaderQueryResult empty = new LoaderQueryResult(now, now, List.of(), 0);
    assertTrue(empty.isEmpty());

    // Non-empty result
    LoaderQueryResult nonEmpty = new LoaderQueryResult(now, now,
        List.of(Map.of("timestamp", 1706353200L)), 1);
    assertFalse(nonEmpty.isEmpty());
  }
}
