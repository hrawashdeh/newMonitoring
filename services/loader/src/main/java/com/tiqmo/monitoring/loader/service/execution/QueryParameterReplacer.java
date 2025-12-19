package com.tiqmo.monitoring.loader.service.execution;

/**
 * Replaces time placeholder parameters in loader SQL queries.
 *
 * <p>Supports two placeholder formats:
 * <ul>
 *   <li>{@code :fromTime} - Start of time window (inclusive)</li>
 *   <li>{@code :toTime} - End of time window (exclusive)</li>
 * </ul>
 *
 * <p><b>Timestamp Formats:</b>
 * <ul>
 *   <li><b>ISO-8601</b>: {@code 2024-01-27T10:30:00Z} (default, recommended)</li>
 *   <li><b>Unix Epoch</b>: {@code 1706354400} (seconds since 1970-01-01)</li>
 *   <li><b>Unix Epoch Millis</b>: {@code 1706354400000} (milliseconds)</li>
 * </ul>
 *
 * <p><b>Format Detection:</b>
 * The service auto-detects the required format based on SQL context:
 * <ul>
 *   <li>If SQL contains {@code TIMESTAMP '...'} or {@code TO_TIMESTAMP}: Use ISO-8601</li>
 *   <li>If SQL contains {@code UNIX_TIMESTAMP} or numeric comparison: Use Unix epoch</li>
 *   <li>Default: ISO-8601 with timezone (UTC)</li>
 * </ul>
 *
 * <p><b>Example SQL Queries:</b>
 *
 * <p><b>PostgreSQL (ISO-8601):</b>
 * <pre>
 * SELECT timestamp, value, record_count
 * FROM source_table
 * WHERE timestamp >= TIMESTAMP ':fromTime'
 *   AND timestamp < TIMESTAMP ':toTime'
 * </pre>
 * After replacement:
 * <pre>
 * SELECT timestamp, value, record_count
 * FROM source_table
 * WHERE timestamp >= TIMESTAMP '2024-01-27T10:00:00Z'
 *   AND timestamp < TIMESTAMP '2024-01-27T15:00:00Z'
 * </pre>
 *
 * <p><b>MySQL (Unix Epoch):</b>
 * <pre>
 * SELECT FROM_UNIXTIME(timestamp_unix) as timestamp, value, count
 * FROM source_table
 * WHERE timestamp_unix >= :fromTime
 *   AND timestamp_unix < :toTime
 * </pre>
 * After replacement:
 * <pre>
 * SELECT FROM_UNIXTIME(timestamp_unix) as timestamp, value, count
 * FROM source_table
 * WHERE timestamp_unix >= 1706354400
 *   AND timestamp_unix < 1706372400
 * </pre>
 *
 * <p><b>UTF-8 Support:</b>
 * <pre>
 * -- Arabic column names and comments supported
 * SELECT الطابع_الزمني as timestamp, القيمة as value
 * FROM جدول_المصدر
 * WHERE الطابع_الزمني >= TIMESTAMP ':fromTime'
 *   AND الطابع_الزمني < TIMESTAMP ':toTime'
 * </pre>
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe for concurrent use.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public interface QueryParameterReplacer {

  /**
   * Replaces time placeholders in SQL with actual timestamps.
   *
   * <p>Auto-detects required timestamp format based on SQL context.
   * Supports UTF-8 encoded SQL (Arabic characters, emoji, etc.).
   *
   * @param sql the SQL query with :fromTime and :toTime placeholders
   * @param window the time window containing actual timestamps
   * @return SQL with placeholders replaced by formatted timestamps
   * @throws IllegalArgumentException if sql or window is null, or sql is blank
   */
  String replacePlaceholders(String sql, TimeWindow window);

  /**
   * Replaces time placeholders using explicit timestamp format.
   *
   * <p>Use this method when you need to override auto-detection.
   *
   * @param sql the SQL query with placeholders
   * @param window the time window
   * @param format the timestamp format to use
   * @return SQL with placeholders replaced
   * @throws IllegalArgumentException if any parameter is null
   */
  String replacePlaceholders(String sql, TimeWindow window, TimestampFormat format);

  /**
   * Replaces time placeholders with timezone offset adjustment.
   *
   * <p><b>Issue #2.1:</b> Handles source databases in different timezones.
   *
   * <p>The window times are in UTC. The timezone offset is subtracted to get
   * the source database local times for the query.
   *
   * <p>Example: UTC window 14:00-15:00, source timezone GMT+4 (offset=4)
   * → Query for 10:00-11:00 in source DB
   *
   * @param sql the SQL query with placeholders
   * @param window the time window (in UTC)
   * @param timezoneOffsetHours timezone offset of source DB (e.g., 4 for GMT+4, -5 for EST)
   * @return SQL with placeholders replaced by adjusted timestamps
   * @throws IllegalArgumentException if any parameter is invalid
   */
  String replacePlaceholders(String sql, TimeWindow window, Integer timezoneOffsetHours);

  /**
   * Supported timestamp formats for SQL replacement.
   */
  enum TimestampFormat {
    /**
     * ISO-8601 format with UTC timezone: {@code 2024-01-27T10:30:00Z}
     * <p>Recommended for PostgreSQL, Oracle, most SQL databases.
     */
    ISO_8601,

    /**
     * MySQL STR_TO_DATE format: {@code 2024-01-27 10:30}
     * <p>Used with MySQL STR_TO_DATE function and format '%Y-%m-%d %H:%i'
     */
    MYSQL_DATETIME,

    /**
     * Unix epoch seconds: {@code 1706354400}
     * <p>Used for integer timestamp columns.
     */
    UNIX_EPOCH_SECONDS,

    /**
     * Unix epoch milliseconds: {@code 1706354400000}
     * <p>Used for bigint timestamp columns storing milliseconds.
     */
    UNIX_EPOCH_MILLIS
  }
}
