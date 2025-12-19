package com.tiqmo.monitoring.loader.service.execution;

import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import com.tiqmo.monitoring.loader.service.signals.SegmentCombinationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of DataTransformer.
 *
 * <p>Transforms query result rows to SignalsHistory entities with flexible
 * column name matching and type conversion.
 *
 * <p><b>Segment Handling:</b>
 * <ul>
 *   <li>Extracts 10 segment fields (seg1-seg10) from query results</li>
 *   <li>Looks up segment_combination by (loader_code + seg1-seg10)</li>
 *   <li>If not found, creates new entry with auto-incremented segment_code</li>
 *   <li>Stores segment_code in signals_history</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultDataTransformer implements DataTransformer {

  private final SegmentCombinationService segmentCombinationService;

  // Column name variations (case-insensitive matching)
  private static final String[] TIMESTAMP_COLUMNS = {"timestamp", "load_time_stamp", "ts", "time"};
  private static final String[] SEG1_COLUMNS = {"seg1", "segment1", "segment_1"};
  private static final String[] SEG2_COLUMNS = {"seg2", "segment2", "segment_2"};
  private static final String[] SEG3_COLUMNS = {"seg3", "segment3", "segment_3"};
  private static final String[] SEG4_COLUMNS = {"seg4", "segment4", "segment_4"};
  private static final String[] SEG5_COLUMNS = {"seg5", "segment5", "segment_5"};
  private static final String[] SEG6_COLUMNS = {"seg6", "segment6", "segment_6"};
  private static final String[] SEG7_COLUMNS = {"seg7", "segment7", "segment_7"};
  private static final String[] SEG8_COLUMNS = {"seg8", "segment8", "segment_8"};
  private static final String[] SEG9_COLUMNS = {"seg9", "segment9", "segment_9"};
  private static final String[] SEG10_COLUMNS = {"seg10", "segment10", "segment_10"};
  private static final String[] REC_COUNT_COLUMNS = {"rec_count", "record_count", "count", "cnt"};
  private static final String[] MAX_VAL_COLUMNS = {"max_val", "max", "maximum"};
  private static final String[] MIN_VAL_COLUMNS = {"min_val", "min", "minimum"};
  private static final String[] AVG_VAL_COLUMNS = {"avg_val", "avg", "average"};
  private static final String[] SUM_VAL_COLUMNS = {"sum_val", "sum", "total"};

  @Override
  public List<SignalsHistory> transform(String loaderCode, LoaderQueryResult queryResult)
      throws TransformationException {
    // Delegate to timezone-aware method with no offset (0 = UTC, no conversion)
    return transform(loaderCode, queryResult, 0);
  }

  @Override
  public List<SignalsHistory> transform(String loaderCode, LoaderQueryResult queryResult,
                                         Integer timezoneOffsetHours)
      throws TransformationException {

    validateInputs(loaderCode, queryResult);

    if (queryResult.isEmpty()) {
      log.debug("Query result is empty for loader: {}", loaderCode);
      return List.of();
    }

    long createTime = System.currentTimeMillis() / 1000; // Unix epoch seconds
    List<SignalsHistory> results = new ArrayList<>(queryResult.rows().size());

    // Issue #2.1: Calculate timezone offset in seconds
    // Add offset to normalize source DB times to UTC
    // Example: Source 10:00 GMT+4 (offset=4) → UTC 14:00 (+4 hours)
    long timezoneOffsetSeconds = (timezoneOffsetHours != null ? timezoneOffsetHours : 0) * 3600L;

    if (timezoneOffsetSeconds != 0) {
      log.info("Normalizing timestamps to UTC: adding {} hours ({} seconds) to source timestamps",
          timezoneOffsetHours, timezoneOffsetSeconds);
    }

    int rowIndex = 0;
    for (Map<String, Object> row : queryResult.rows()) {
      try {
        SignalsHistory signal = transformRow(loaderCode, row, createTime, rowIndex, timezoneOffsetSeconds);
        results.add(signal);
      } catch (Exception e) {
        throw new TransformationException(
            String.format("Failed to transform row %d for loader %s: %s",
                rowIndex, loaderCode, e.getMessage()),
            e
        );
      }
      rowIndex++;
    }

    log.debug("Transformed {} rows for loader: {} (timezone offset: {} hours)",
        results.size(), loaderCode, timezoneOffsetHours != null ? timezoneOffsetHours : 0);
    return results;
  }

  /**
   * Transforms a single row to SignalsHistory entity.
   *
   * @param loaderCode the loader code
   * @param row the row data
   * @param createTime the creation timestamp
   * @param rowIndex the row index (for error messages)
   * @param timezoneOffsetSeconds timezone offset in seconds to add to timestamp (for UTC normalization)
   * @return SignalsHistory entity
   * @throws TransformationException if required field is missing or conversion fails
   */
  private SignalsHistory transformRow(String loaderCode, Map<String, Object> row,
                                     long createTime, int rowIndex, long timezoneOffsetSeconds)
      throws TransformationException {

    // Required field: timestamp
    Long loadTimeStamp = extractTimestamp(row, rowIndex);

    // Issue #2.1: Normalize timestamp to UTC by adding timezone offset
    // Example: Source 10:00 GMT+4, offset=4*3600 → UTC 14:00
    if (timezoneOffsetSeconds != 0) {
      loadTimeStamp += timezoneOffsetSeconds;
    }

    // Extract 10 segment fields
    String seg1 = extractString(row, SEG1_COLUMNS);
    String seg2 = extractString(row, SEG2_COLUMNS);
    String seg3 = extractString(row, SEG3_COLUMNS);
    String seg4 = extractString(row, SEG4_COLUMNS);
    String seg5 = extractString(row, SEG5_COLUMNS);
    String seg6 = extractString(row, SEG6_COLUMNS);
    String seg7 = extractString(row, SEG7_COLUMNS);
    String seg8 = extractString(row, SEG8_COLUMNS);
    String seg9 = extractString(row, SEG9_COLUMNS);
    String seg10 = extractString(row, SEG10_COLUMNS);

    // Get or create segment_code via SegmentCombinationService
    Long segmentCode = segmentCombinationService.getOrCreateSegmentCode(
        loaderCode, seg1, seg2, seg3, seg4, seg5, seg6, seg7, seg8, seg9, seg10
    );

    // Extract metric fields
    Long recCount = extractLong(row, REC_COUNT_COLUMNS);
    Double maxVal = extractDouble(row, MAX_VAL_COLUMNS);
    Double minVal = extractDouble(row, MIN_VAL_COLUMNS);
    Double avgVal = extractDouble(row, AVG_VAL_COLUMNS);
    Double sumVal = extractDouble(row, SUM_VAL_COLUMNS);

    return SignalsHistory.builder()
        .loaderCode(loaderCode)
        .loadTimeStamp(loadTimeStamp)
        .segmentCode(String.valueOf(segmentCode))
        .recCount(recCount)
        .maxVal(maxVal)
        .minVal(minVal)
        .avgVal(avgVal)
        .sumVal(sumVal)
        .createTime(createTime)
        .build();
  }

  /**
   * Extracts timestamp from row (required field).
   *
   * <p>Supports multiple types:
   * <ul>
   *   <li>Long: Unix epoch seconds or milliseconds (auto-detected)</li>
   *   <li>Integer: Unix epoch seconds</li>
   *   <li>Instant: Converted to epoch seconds</li>
   *   <li>java.sql.Timestamp: From JDBC ResultSet (converted to epoch seconds)</li>
   *   <li>java.util.Date: Legacy date type (converted to epoch seconds)</li>
   *   <li>String: Parsed as ISO-8601 or Unix epoch</li>
   * </ul>
   *
   * @param row the row data
   * @param rowIndex the row index (for error messages)
   * @return timestamp as Unix epoch seconds
   * @throws TransformationException if timestamp is missing or invalid
   */
  private Long extractTimestamp(Map<String, Object> row, int rowIndex)
      throws TransformationException {

    Object value = findValue(row, TIMESTAMP_COLUMNS);

    if (value == null) {
      throw new TransformationException(
          String.format("Missing required field 'timestamp' in row %d. " +
              "Expected one of: %s", rowIndex, String.join(", ", TIMESTAMP_COLUMNS))
      );
    }

    try {
      // Long (epoch seconds or millis)
      if (value instanceof Long longValue) {
        // Auto-detect: if > year 3000 in seconds (94608000000), it's likely millis
        if (longValue > 94608000000L) {
          return longValue / 1000; // Convert millis to seconds
        }
        return longValue;
      }

      // Integer (epoch seconds)
      if (value instanceof Integer intValue) {
        return intValue.longValue();
      }

      // Instant
      if (value instanceof Instant instant) {
        return instant.getEpochSecond();
      }

      // java.sql.Timestamp (from JDBC ResultSet)
      if (value instanceof java.sql.Timestamp timestamp) {
        return timestamp.getTime() / 1000; // Convert millis to seconds
      }

      // java.util.Date (legacy)
      if (value instanceof java.util.Date date) {
        return date.getTime() / 1000;
      }

      // BigDecimal (from MySQL UNIX_TIMESTAMP() function)
      if (value instanceof java.math.BigDecimal bigDecimalValue) {
        long epochValue = bigDecimalValue.longValue();
        // Auto-detect: if > year 3000 in seconds (94608000000), it's likely millis
        if (epochValue > 94608000000L) {
          return epochValue / 1000; // Convert millis to seconds
        }
        return epochValue;
      }

      // Double/Float (decimal epoch values)
      if (value instanceof Double doubleValue) {
        long epochValue = doubleValue.longValue();
        if (epochValue > 94608000000L) {
          return epochValue / 1000;
        }
        return epochValue;
      }

      if (value instanceof Float floatValue) {
        long epochValue = floatValue.longValue();
        if (epochValue > 94608000000L) {
          return epochValue / 1000;
        }
        return epochValue;
      }

      // String (ISO-8601 or epoch)
      if (value instanceof String strValue) {
        // Try parsing as epoch seconds/millis
        try {
          long epochValue = Long.parseLong(strValue);
          if (epochValue > 94608000000L) {
            return epochValue / 1000;
          }
          return epochValue;
        } catch (NumberFormatException e) {
          // Try parsing as ISO-8601
          return Instant.parse(strValue).getEpochSecond();
        }
      }

      throw new TransformationException(
          String.format("Unsupported timestamp type in row %d: %s (value: %s)",
              rowIndex, value.getClass().getSimpleName(), value)
      );

    } catch (TransformationException e) {
      throw e;
    } catch (Exception e) {
      throw new TransformationException(
          String.format("Failed to parse timestamp in row %d: %s", rowIndex, value),
          e
      );
    }
  }

  /**
   * Extracts String value from row (optional field).
   *
   * @param row the row data
   * @param columnNames possible column names
   * @return string value or null if not found
   */
  private String extractString(Map<String, Object> row, String[] columnNames) {
    Object value = findValue(row, columnNames);
    if (value == null) {
      return null;
    }
    return value.toString();
  }

  /**
   * Extracts Long value from row (optional field).
   *
   * @param row the row data
   * @param columnNames possible column names
   * @return long value or null if not found
   */
  private Long extractLong(Map<String, Object> row, String[] columnNames) {
    Object value = findValue(row, columnNames);
    if (value == null) {
      return null;
    }

    try {
      if (value instanceof Long longValue) {
        return longValue;
      }
      if (value instanceof Integer intValue) {
        return intValue.longValue();
      }
      if (value instanceof Number numberValue) {
        return numberValue.longValue();
      }
      if (value instanceof String strValue) {
        return Long.parseLong(strValue);
      }
      log.warn("Unexpected type for Long field: {} (value: {})",
          value.getClass().getSimpleName(), value);
      return null;
    } catch (Exception e) {
      log.warn("Failed to parse Long value: {}", value, e);
      return null;
    }
  }

  /**
   * Extracts Double value from row (optional field).
   *
   * @param row the row data
   * @param columnNames possible column names
   * @return double value or null if not found
   */
  private Double extractDouble(Map<String, Object> row, String[] columnNames) {
    Object value = findValue(row, columnNames);
    if (value == null) {
      return null;
    }

    try {
      if (value instanceof Double doubleValue) {
        return doubleValue;
      }
      if (value instanceof Float floatValue) {
        return floatValue.doubleValue();
      }
      if (value instanceof Number numberValue) {
        return numberValue.doubleValue();
      }
      if (value instanceof String strValue) {
        return Double.parseDouble(strValue);
      }
      log.warn("Unexpected type for Double field: {} (value: {})",
          value.getClass().getSimpleName(), value);
      return null;
    } catch (Exception e) {
      log.warn("Failed to parse Double value: {}", value, e);
      return null;
    }
  }

  /**
   * Finds a value in the row by checking multiple column name variations.
   * Uses case-insensitive matching.
   *
   * @param row the row data
   * @param columnNames possible column names
   * @return the value or null if not found
   */
  private Object findValue(Map<String, Object> row, String[] columnNames) {
    for (String columnName : columnNames) {
      // Try exact match first
      if (row.containsKey(columnName)) {
        return row.get(columnName);
      }

      // Try case-insensitive match
      for (Map.Entry<String, Object> entry : row.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(columnName)) {
          return entry.getValue();
        }
      }
    }
    return null;
  }

  /**
   * Validates inputs for transformation.
   *
   * @param loaderCode the loader code
   * @param queryResult the query result
   * @throws IllegalArgumentException if validation fails
   */
  private void validateInputs(String loaderCode, LoaderQueryResult queryResult) {
    if (loaderCode == null || loaderCode.isBlank()) {
      throw new IllegalArgumentException("Loader code cannot be null or blank");
    }
    if (queryResult == null) {
      throw new IllegalArgumentException("Query result cannot be null");
    }
  }
}
