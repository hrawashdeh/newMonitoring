package com.tiqmo.monitoring.loader.service.execution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of QueryParameterReplacer.
 *
 * <p>Features:
 * <ul>
 *   <li>Auto-detects timestamp format from SQL context</li>
 *   <li>Supports UTF-8 encoded SQL (Arabic characters, emoji, etc.)</li>
 *   <li>Thread-safe for concurrent use</li>
 *   <li>Validates placeholders exist before replacement</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
public class DefaultQueryParameterReplacer implements QueryParameterReplacer {

  private static final String FROM_TIME_PLACEHOLDER = ":fromTime";
  private static final String TO_TIME_PLACEHOLDER = ":toTime";

  // ISO-8601 formatter with UTC timezone
  private static final DateTimeFormatter ISO_8601_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
          .withZone(ZoneOffset.UTC);

  // MySQL STR_TO_DATE formatter (format: '2024-01-27 10:30')
  private static final DateTimeFormatter MYSQL_DATETIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
          .withZone(ZoneOffset.UTC);

  // Patterns for format detection
  private static final Pattern UNIX_TIMESTAMP_PATTERN =
      Pattern.compile("UNIX_TIMESTAMP|FROM_UNIXTIME|timestamp_unix|epoch",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern STR_TO_DATE_PATTERN =
      Pattern.compile("STR_TO_DATE",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern TIMESTAMP_KEYWORD_PATTERN =
      Pattern.compile("TIMESTAMP\\s+['\"]|TO_TIMESTAMP|CAST\\s*\\(.*AS\\s+TIMESTAMP",
          Pattern.CASE_INSENSITIVE);

  @Override
  public String replacePlaceholders(String sql, TimeWindow window) {
    validateInputs(sql, window);

    // Auto-detect format
    TimestampFormat format = detectFormat(sql);

    log.info("Auto-detected timestamp format: {} for SQL: {}",
        format, truncateForLog(sql));

    return replacePlaceholders(sql, window, format);
  }

  @Override
  public String replacePlaceholders(String sql, TimeWindow window, TimestampFormat format) {
    validateInputs(sql, window);

    if (format == null) {
      throw new IllegalArgumentException("TimestampFormat cannot be null");
    }

    // Format timestamps
    String fromTimeValue = formatTimestamp(window.fromTime(), format);
    String toTimeValue = formatTimestamp(window.toTime(), format);

    log.info("Formatting timestamps with format {}: fromTime={} ({}), toTime={} ({})",
        format, window.fromTime(), fromTimeValue, window.toTime(), toTimeValue);

    // Replace placeholders (preserves UTF-8 characters)
    String result = sql.replace(FROM_TIME_PLACEHOLDER, fromTimeValue);
    result = result.replace(TO_TIME_PLACEHOLDER, toTimeValue);

    log.info("Replaced placeholders: :fromTime={}, :toTime={}",
        fromTimeValue, toTimeValue);

    return result;
  }

  @Override
  public String replacePlaceholders(String sql, TimeWindow window, Integer timezoneOffsetHours) {
    validateInputs(sql, window);

    if (timezoneOffsetHours == null || timezoneOffsetHours == 0) {
      // No timezone conversion needed - use standard method
      return replacePlaceholders(sql, window);
    }

    // Issue #2.1: Apply timezone offset
    // UTC times need to be adjusted to source DB timezone for the query
    // Subtract offset to get source DB local times
    // Example: UTC 14:00, GMT+4 source → query for 10:00 in source DB
    long offsetSeconds = timezoneOffsetHours * 3600L;
    Instant adjustedFromTime = window.fromTime().minusSeconds(offsetSeconds);
    Instant adjustedToTime = window.toTime().minusSeconds(offsetSeconds);

    log.info("Applying timezone offset {} hours: UTC window [{} to {}] → Source DB window [{} to {}]",
        timezoneOffsetHours,
        window.fromTime(), window.toTime(),
        adjustedFromTime, adjustedToTime);

    // Create adjusted window and use standard replacement
    TimeWindow adjustedWindow = new TimeWindow(adjustedFromTime, adjustedToTime);
    return replacePlaceholders(sql, adjustedWindow);
  }

  /**
   * Auto-detects timestamp format from SQL context.
   *
   * <p>Detection rules:
   * <ol>
   *   <li>If SQL contains UNIX_TIMESTAMP, FROM_UNIXTIME, or similar: UNIX_EPOCH_SECONDS</li>
   *   <li>If SQL contains STR_TO_DATE: MYSQL_DATETIME</li>
   *   <li>If SQL contains TIMESTAMP keyword or TO_TIMESTAMP: ISO_8601</li>
   *   <li>Default: ISO_8601 (most compatible)</li>
   * </ol>
   *
   * @param sql the SQL to analyze
   * @return detected timestamp format
   */
  private TimestampFormat detectFormat(String sql) {
    // Check for MySQL STR_TO_DATE function FIRST (used in WHERE clause with placeholders)
    Matcher strToDateMatcher = STR_TO_DATE_PATTERN.matcher(sql);
    if (strToDateMatcher.find()) {
      return TimestampFormat.MYSQL_DATETIME;
    }

    // Check for Unix timestamp indicators (might be in SELECT clause)
    Matcher unixMatcher = UNIX_TIMESTAMP_PATTERN.matcher(sql);
    if (unixMatcher.find()) {
      return TimestampFormat.UNIX_EPOCH_SECONDS;
    }

    // Check for TIMESTAMP keyword (implies ISO-8601 string)
    Matcher timestampMatcher = TIMESTAMP_KEYWORD_PATTERN.matcher(sql);
    if (timestampMatcher.find()) {
      return TimestampFormat.ISO_8601;
    }

    // Default to ISO-8601 (most widely supported)
    return TimestampFormat.ISO_8601;
  }

  /**
   * Formats an Instant to the specified timestamp format.
   *
   * @param instant the instant to format
   * @param format the target format
   * @return formatted timestamp string
   */
  private String formatTimestamp(Instant instant, TimestampFormat format) {
    return switch (format) {
      case ISO_8601 -> ISO_8601_FORMATTER.format(instant);
      case MYSQL_DATETIME -> MYSQL_DATETIME_FORMATTER.format(instant);
      case UNIX_EPOCH_SECONDS -> String.valueOf(instant.getEpochSecond());
      case UNIX_EPOCH_MILLIS -> String.valueOf(instant.toEpochMilli());
    };
  }

  /**
   * Validates inputs for placeholder replacement.
   *
   * @param sql the SQL to validate
   * @param window the time window to validate
   * @throws IllegalArgumentException if validation fails
   */
  private void validateInputs(String sql, TimeWindow window) {
    if (sql == null) {
      throw new IllegalArgumentException("SQL cannot be null");
    }
    if (sql.isBlank()) {
      throw new IllegalArgumentException("SQL cannot be blank");
    }
    if (window == null) {
      throw new IllegalArgumentException("TimeWindow cannot be null");
    }

    // Warn if placeholders are missing (might be intentional)
    if (!sql.contains(FROM_TIME_PLACEHOLDER) && !sql.contains(TO_TIME_PLACEHOLDER)) {
      log.warn("SQL does not contain :fromTime or :toTime placeholders. SQL: {}",
          truncateForLog(sql));
    }
  }

  /**
   * Truncates SQL for logging (avoids huge log messages).
   *
   * @param sql the SQL to truncate
   * @return truncated SQL (max 200 chars)
   */
  private String truncateForLog(String sql) {
    if (sql.length() <= 200) {
      return sql;
    }
    return sql.substring(0, 200) + "... (truncated)";
  }
}
