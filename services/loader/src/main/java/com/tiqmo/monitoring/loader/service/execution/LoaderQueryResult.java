package com.tiqmo.monitoring.loader.service.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of executing a loader SQL query.
 *
 * <p>This is a simple DTO that holds the raw query results before transformation
 * to SignalsHistory entities. Each row is represented as a Map of column names
 * to values.
 *
 * <p><b>Expected Columns:</b>
 * <ul>
 *   <li><b>timestamp</b> (required): Load timestamp (Instant, Long epoch, or String ISO-8601)</li>
 *   <li><b>segment_code</b> (optional): Segment identifier</li>
 *   <li><b>rec_count</b> (optional): Record count</li>
 *   <li><b>max_val</b> (optional): Maximum value</li>
 *   <li><b>min_val</b> (optional): Minimum value</li>
 *   <li><b>avg_val</b> (optional): Average value</li>
 * </ul>
 *
 * <p><b>Column Name Variations Supported:</b>
 * <ul>
 *   <li>timestamp, load_time_stamp, ts, time</li>
 *   <li>segment_code, segment, seg</li>
 *   <li>rec_count, record_count, count, cnt</li>
 *   <li>max_val, max, maximum</li>
 *   <li>min_val, min, minimum</li>
 *   <li>avg_val, avg, average</li>
 * </ul>
 *
 * @param queryFromTime Start of query time window (for metadata)
 * @param queryToTime End of query time window (for metadata)
 * @param rows Query result rows (each row is a Map of column name → value)
 * @param rowCount Number of rows returned
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public record LoaderQueryResult(
    Instant queryFromTime,
    Instant queryToTime,
    List<Map<String, Object>> rows,
    long rowCount
) {

  /**
   * Creates a LoaderQueryResult with validation.
   *
   * @throws IllegalArgumentException if required fields are null
   */
  public LoaderQueryResult {
    if (queryFromTime == null) {
      throw new IllegalArgumentException("queryFromTime cannot be null");
    }
    if (queryToTime == null) {
      throw new IllegalArgumentException("queryToTime cannot be null");
    }
    if (rows == null) {
      throw new IllegalArgumentException("rows cannot be null");
    }
    if (rowCount < 0) {
      throw new IllegalArgumentException("rowCount cannot be negative");
    }
  }

  /**
   * Returns true if result has no rows.
   */
  public boolean isEmpty() {
    return rows.isEmpty();
  }
}
