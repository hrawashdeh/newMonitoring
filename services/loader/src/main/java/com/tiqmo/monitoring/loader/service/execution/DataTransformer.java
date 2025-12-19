package com.tiqmo.monitoring.loader.service.execution;

import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;

import java.util.List;

/**
 * Transforms loader query results to SignalsHistory entities.
 *
 * <p>Handles dynamic column mapping, type conversion, and validation.
 * Supports multiple column name variations for flexibility.
 *
 * <p><b>Column Mapping:</b>
 * <table>
 *   <tr><th>SignalsHistory Field</th><th>Source Column Variations</th><th>Required</th></tr>
 *   <tr><td>loadTimeStamp</td><td>timestamp, load_time_stamp, ts, time</td><td>✅ Yes</td></tr>
 *   <tr><td>segmentCode</td><td>segment_code, segment, seg</td><td>❌ No</td></tr>
 *   <tr><td>recCount</td><td>rec_count, record_count, count, cnt</td><td>❌ No</td></tr>
 *   <tr><td>maxVal</td><td>max_val, max, maximum</td><td>❌ No</td></tr>
 *   <tr><td>minVal</td><td>min_val, min, minimum</td><td>❌ No</td></tr>
 *   <tr><td>avgVal</td><td>avg_val, avg, average</td><td>❌ No</td></tr>
 * </table>
 *
 * <p><b>Type Conversion:</b>
 * <ul>
 *   <li><b>loadTimeStamp</b>: Accepts Long (epoch seconds/millis), Instant, String (ISO-8601)</li>
 *   <li><b>segmentCode</b>: Accepts String, Number (converted to string)</li>
 *   <li><b>recCount</b>: Accepts Long, Integer, String (parsed)</li>
 *   <li><b>maxVal/minVal/avgVal</b>: Accepts Double, Float, Number, String (parsed)</li>
 * </ul>
 *
 * <p><b>Error Handling:</b>
 * <ul>
 *   <li>Missing required field (timestamp) → throws TransformationException</li>
 *   <li>Invalid type conversion → throws TransformationException with details</li>
 *   <li>Missing optional fields → set to null (allowed)</li>
 *   <li>Extra columns → ignored</li>
 * </ul>
 *
 * <p><b>Example Usage:</b>
 * <pre>
 * LoaderQueryResult result = executeQuery(sql, window);
 * List&lt;SignalsHistory&gt; signals = dataTransformer.transform("MY_LOADER", result);
 * signalsIngestService.bulkAppend("MY_LOADER", signals);
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public interface DataTransformer {

  /**
   * Transforms query result rows to SignalsHistory entities.
   *
   * <p>Sets loaderCode and createTime for all entities.
   * Validates required fields (timestamp).
   *
   * @param loaderCode the loader code to assign to all entities
   * @param queryResult the query result to transform
   * @return list of SignalsHistory entities (empty list if no rows)
   * @throws IllegalArgumentException if loaderCode is null/blank or queryResult is null
   * @throws TransformationException if transformation fails (missing required field, invalid type)
   */
  List<SignalsHistory> transform(String loaderCode, LoaderQueryResult queryResult)
      throws TransformationException;

  /**
   * Transforms query result rows to SignalsHistory entities with timezone normalization.
   *
   * <p><b>Issue #2.1:</b> Normalizes source database timestamps to UTC.
   *
   * <p>The source data timestamps are in local timezone (e.g., GMT+4).
   * The timezone offset is added to normalize them to UTC for storage.
   *
   * <p>Example: Source timestamp 10:00 GMT+4 (offset=4) → stored as 14:00 UTC
   *
   * @param loaderCode the loader code to assign to all entities
   * @param queryResult the query result to transform
   * @param timezoneOffsetHours timezone offset of source DB (e.g., 4 for GMT+4, -5 for EST)
   * @return list of SignalsHistory entities with normalized timestamps
   * @throws IllegalArgumentException if loaderCode is null/blank or queryResult is null
   * @throws TransformationException if transformation fails
   */
  List<SignalsHistory> transform(String loaderCode, LoaderQueryResult queryResult, Integer timezoneOffsetHours)
      throws TransformationException;

  /**
   * Exception thrown when transformation fails.
   */
  class TransformationException extends RuntimeException {
    public TransformationException(String message) {
      super(message);
    }

    public TransformationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
