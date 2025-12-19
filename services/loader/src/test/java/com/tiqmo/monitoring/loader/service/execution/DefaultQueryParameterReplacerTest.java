package com.tiqmo.monitoring.loader.service.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultQueryParameterReplacer.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
class DefaultQueryParameterReplacerTest {

  private QueryParameterReplacer replacer;
  private TimeWindow testWindow;

  @BeforeEach
  void setUp() {
    replacer = new DefaultQueryParameterReplacer();

    // Fixed test window: 2024-01-27 10:00:00 to 15:00:00 UTC
    Instant fromTime = Instant.parse("2024-01-27T10:00:00Z");
    Instant toTime = Instant.parse("2024-01-27T15:00:00Z");
    testWindow = new TimeWindow(fromTime, toTime);
  }

  @Test
  void testReplacePlaceholders_PostgreSQL_ISO8601() {
    // Arrange
    String sql = """
        SELECT timestamp, value, record_count
        FROM source_table
        WHERE timestamp >= TIMESTAMP ':fromTime'
          AND timestamp < TIMESTAMP ':toTime'
        """;

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("TIMESTAMP '2024-01-27T10:00:00Z'"),
        "Should contain fromTime as ISO-8601");
    assertTrue(result.contains("TIMESTAMP '2024-01-27T15:00:00Z'"),
        "Should contain toTime as ISO-8601");
    assertFalse(result.contains(":fromTime"), "Should not contain placeholder");
    assertFalse(result.contains(":toTime"), "Should not contain placeholder");
  }

  @Test
  void testReplacePlaceholders_MySQL_UnixEpoch() {
    // Arrange
    String sql = """
        SELECT FROM_UNIXTIME(timestamp_unix) as timestamp, value, count
        FROM source_table
        WHERE timestamp_unix >= :fromTime
          AND timestamp_unix < :toTime
        """;

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    // Should detect UNIX format from FROM_UNIXTIME
    assertTrue(result.contains("1706349600"), "Should contain fromTime as Unix epoch");
    assertTrue(result.contains("1706367600"), "Should contain toTime as Unix epoch");
    assertFalse(result.contains(":fromTime"));
    assertFalse(result.contains(":toTime"));
  }

  @Test
  void testReplacePlaceholders_AutoDetectISO8601() {
    // Arrange - SQL with TIMESTAMP keyword → should use ISO-8601
    String sql = "SELECT * FROM table WHERE ts >= TIMESTAMP ':fromTime'";

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("2024-01-27T10:00:00Z"),
        "Auto-detect should use ISO-8601 for TIMESTAMP keyword");
  }

  @Test
  void testReplacePlaceholders_AutoDetectUnixEpoch() {
    // Arrange - SQL with UNIX_TIMESTAMP → should use Unix epoch
    String sql = "SELECT * FROM table WHERE UNIX_TIMESTAMP(ts) >= :fromTime";

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("1706349600"),
        "Auto-detect should use Unix epoch for UNIX_TIMESTAMP");
  }

  @Test
  void testReplacePlaceholders_DefaultToISO8601() {
    // Arrange - SQL with no format hints → should default to ISO-8601
    String sql = "SELECT * FROM table WHERE ts >= ':fromTime' AND ts < ':toTime'";

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("2024-01-27T10:00:00Z"),
        "Should default to ISO-8601 when no hints found");
    assertTrue(result.contains("2024-01-27T15:00:00Z"));
  }

  @Test
  void testReplacePlaceholders_ExplicitISO8601() {
    // Arrange
    String sql = "SELECT * FROM table WHERE ts >= ':fromTime'";

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow,
        QueryParameterReplacer.TimestampFormat.ISO_8601);

    // Assert
    assertTrue(result.contains("2024-01-27T10:00:00Z"),
        "Explicit format should be respected");
  }

  @Test
  void testReplacePlaceholders_ExplicitUnixEpochSeconds() {
    // Arrange
    String sql = "SELECT * FROM table WHERE epoch >= :fromTime AND epoch < :toTime";

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow,
        QueryParameterReplacer.TimestampFormat.UNIX_EPOCH_SECONDS);

    // Assert
    assertTrue(result.contains("1706349600"), "Should use Unix seconds");
    assertTrue(result.contains("1706367600"));
    assertFalse(result.contains("1706349600000"), "Should not contain milliseconds");
  }

  @Test
  void testReplacePlaceholders_ExplicitUnixEpochMillis() {
    // Arrange
    String sql = "SELECT * FROM table WHERE epoch_ms >= :fromTime";

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow,
        QueryParameterReplacer.TimestampFormat.UNIX_EPOCH_MILLIS);

    // Assert
    assertTrue(result.contains("1706349600000"), "Should use Unix milliseconds");
  }

  @Test
  void testReplacePlaceholders_UTF8_ArabicCharacters() {
    // Arrange - SQL with Arabic column names and comments
    String sql = """
        -- استعلام لجلب البيانات
        SELECT الطابع_الزمني as timestamp, القيمة as value
        FROM جدول_المصدر
        WHERE الطابع_الزمني >= TIMESTAMP ':fromTime'
          AND الطابع_الزمني < TIMESTAMP ':toTime'
        """;

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("الطابع_الزمني"), "Should preserve Arabic characters");
    assertTrue(result.contains("القيمة"), "Should preserve Arabic characters");
    assertTrue(result.contains("جدول_المصدر"), "Should preserve Arabic characters");
    assertTrue(result.contains("2024-01-27T10:00:00Z"), "Should replace placeholders");
    assertFalse(result.contains(":fromTime"), "Should not contain placeholder");
  }

  @Test
  void testReplacePlaceholders_UTF8_Emoji() {
    // Arrange - SQL with emoji in comments
    String sql = """
        -- 🚀 Query for data extraction 📊
        SELECT * FROM table
        WHERE ts >= ':fromTime' -- ⏰ Start time
          AND ts < ':toTime'    -- ⏱️ End time
        """;

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("🚀"), "Should preserve emoji");
    assertTrue(result.contains("📊"), "Should preserve emoji");
    assertTrue(result.contains("⏰"), "Should preserve emoji");
    assertTrue(result.contains("⏱️"), "Should preserve emoji");
    assertTrue(result.contains("2024-01-27T10:00:00Z"));
  }

  @Test
  void testReplacePlaceholders_MultiplePlaceholderOccurrences() {
    // Arrange - SQL with placeholders appearing multiple times
    String sql = """
        SELECT * FROM table WHERE ts >= ':fromTime'
        UNION ALL
        SELECT * FROM archive WHERE ts >= ':fromTime' AND ts < ':toTime'
        """;

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    // All occurrences should be replaced
    assertFalse(result.contains(":fromTime"), "All :fromTime should be replaced");
    assertFalse(result.contains(":toTime"), "All :toTime should be replaced");
    // Count occurrences of replaced value
    int fromTimeCount = result.split("2024-01-27T10:00:00Z", -1).length - 1;
    assertEquals(2, fromTimeCount, "Should replace all :fromTime occurrences");
  }

  @Test
  void testReplacePlaceholders_OnlyFromTime() {
    // Arrange - SQL with only :fromTime placeholder
    String sql = "SELECT * FROM table WHERE ts >= ':fromTime'";

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("2024-01-27T10:00:00Z"));
    assertFalse(result.contains(":fromTime"));
  }

  @Test
  void testReplacePlaceholders_OnlyToTime() {
    // Arrange - SQL with only :toTime placeholder
    String sql = "SELECT * FROM table WHERE ts < ':toTime'";

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("2024-01-27T15:00:00Z"));
    assertFalse(result.contains(":toTime"));
  }

  @Test
  void testReplacePlaceholders_NullSQL_ThrowsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> replacer.replacePlaceholders(null, testWindow),
        "Should throw exception for null SQL");
  }

  @Test
  void testReplacePlaceholders_BlankSQL_ThrowsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> replacer.replacePlaceholders("   ", testWindow),
        "Should throw exception for blank SQL");
  }

  @Test
  void testReplacePlaceholders_NullWindow_ThrowsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> replacer.replacePlaceholders("SELECT * FROM table", null),
        "Should throw exception for null TimeWindow");
  }

  @Test
  void testReplacePlaceholders_NullFormat_ThrowsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> replacer.replacePlaceholders("SELECT * FROM table", testWindow, (QueryParameterReplacer.TimestampFormat) null),
        "Should throw exception for null TimestampFormat");
  }

  @Test
  void testReplacePlaceholders_NoPlaceholders_WarnsButDoesNotFail() {
    // Arrange - SQL without any placeholders
    String sql = "SELECT * FROM table WHERE ts >= '2024-01-27'";

    // Act - should not throw, but log warning
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertEquals(sql, result, "SQL without placeholders should be unchanged");
  }

  @Test
  void testReplacePlaceholders_ComplexQuery_PostgreSQL() {
    // Arrange - Complex query with multiple conditions, JOINs, etc.
    String sql = """
        SELECT
            t1.timestamp,
            t1.value,
            t1.record_count,
            t2.segment_name
        FROM source_table t1
        INNER JOIN segments t2 ON t1.segment_id = t2.id
        WHERE t1.timestamp >= TIMESTAMP ':fromTime'
          AND t1.timestamp < TIMESTAMP ':toTime'
          AND t1.enabled = true
        ORDER BY t1.timestamp ASC
        LIMIT 10000
        """;

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("TIMESTAMP '2024-01-27T10:00:00Z'"));
    assertTrue(result.contains("TIMESTAMP '2024-01-27T15:00:00Z'"));
    assertFalse(result.contains(":fromTime"));
    assertFalse(result.contains(":toTime"));
    assertTrue(result.contains("INNER JOIN"), "Should preserve query structure");
    assertTrue(result.contains("ORDER BY"), "Should preserve query structure");
  }

  @Test
  void testReplacePlaceholders_ComplexQuery_MySQL() {
    // Arrange - MySQL query with date functions
    String sql = """
        SELECT
            DATE_FORMAT(FROM_UNIXTIME(timestamp_unix), '%Y-%m-%d %H:%i:%s') as formatted_time,
            AVG(value) as avg_value,
            COUNT(*) as count
        FROM metrics
        WHERE timestamp_unix >= :fromTime
          AND timestamp_unix < :toTime
        GROUP BY DATE(FROM_UNIXTIME(timestamp_unix))
        HAVING COUNT(*) > 100
        """;

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("1706349600"), "Should use Unix epoch for MySQL");
    assertTrue(result.contains("1706367600"));
    assertFalse(result.contains(":fromTime"));
    assertFalse(result.contains(":toTime"));
    assertTrue(result.contains("DATE_FORMAT"), "Should preserve query structure");
  }

  @Test
  void testReplacePlaceholders_CaseInsensitiveDetection() {
    // Arrange - Mixed case TIMESTAMP keyword
    String sql = "SELECT * FROM table WHERE ts >= timestamp ':fromTime'";

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("2024-01-27T10:00:00Z"),
        "Should detect TIMESTAMP keyword case-insensitively");
  }

  @Test
  void testReplacePlaceholders_TO_TIMESTAMP_Detection() {
    // Arrange - Oracle-style TO_TIMESTAMP function
    String sql = "SELECT * FROM table WHERE ts >= TO_TIMESTAMP(':fromTime', 'YYYY-MM-DD HH24:MI:SS')";

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("2024-01-27T10:00:00Z"),
        "Should detect TO_TIMESTAMP and use ISO-8601");
  }

  @Test
  void testReplacePlaceholders_CAST_AS_TIMESTAMP_Detection() {
    // Arrange - SQL with CAST to TIMESTAMP
    String sql = "SELECT * FROM table WHERE CAST(':fromTime' AS TIMESTAMP) <= ts";

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("2024-01-27T10:00:00Z"),
        "Should detect CAST AS TIMESTAMP and use ISO-8601");
  }

  @Test
  void testReplacePlaceholders_EpochKeyword_Detection() {
    // Arrange - SQL with 'epoch' keyword
    String sql = "SELECT * FROM table WHERE epoch_time >= :fromTime";

    // Act
    String result = replacer.replacePlaceholders(sql, testWindow);

    // Assert
    assertTrue(result.contains("1706349600"),
        "Should detect 'epoch' keyword and use Unix format");
  }

  @Test
  void testTimestampFormat_ISO8601_Format() {
    // Arrange
    Instant instant = Instant.parse("2024-01-27T10:30:45Z");
    TimeWindow window = new TimeWindow(instant, instant.plusSeconds(3600));

    // Act
    String result = replacer.replacePlaceholders(
        "SELECT * FROM table WHERE ts >= TIMESTAMP ':fromTime'",
        window,
        QueryParameterReplacer.TimestampFormat.ISO_8601
    );

    // Assert
    assertTrue(result.contains("2024-01-27T10:30:45Z"),
        "Should format with seconds precision");
  }

  @Test
  void testTimestampFormat_UnixEpochSeconds_Format() {
    // Arrange
    Instant instant = Instant.ofEpochSecond(1706353200);
    TimeWindow window = new TimeWindow(instant, instant.plusSeconds(3600));

    // Act
    String result = replacer.replacePlaceholders(
        "SELECT * FROM table WHERE epoch >= :fromTime",
        window,
        QueryParameterReplacer.TimestampFormat.UNIX_EPOCH_SECONDS
    );

    // Assert
    assertTrue(result.contains("1706353200"),
        "Should format as Unix seconds");
    // Verify it's 10 digits (Unix seconds format)
    String epochStr = "1706353200";
    assertEquals(10, epochStr.length(), "Should be 10 digits");
  }

  @Test
  void testTimestampFormat_UnixEpochMillis_Format() {
    // Arrange
    Instant instant = Instant.ofEpochMilli(1706353200000L);
    TimeWindow window = new TimeWindow(instant, instant.plusSeconds(3600));

    // Act
    String result = replacer.replacePlaceholders(
        "SELECT * FROM table WHERE epoch_ms >= :fromTime",
        window,
        QueryParameterReplacer.TimestampFormat.UNIX_EPOCH_MILLIS
    );

    // Assert
    assertTrue(result.contains("1706353200000"),
        "Should format as Unix milliseconds");
  }
}
