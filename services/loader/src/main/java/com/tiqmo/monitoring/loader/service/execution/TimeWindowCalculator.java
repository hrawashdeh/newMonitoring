package com.tiqmo.monitoring.loader.service.execution;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;

/**
 * Calculates time windows for loader query execution.
 *
 * <p>Determines the from/to timestamp range for each loader run based on:
 * <ul>
 *   <li>Last load timestamp (lastLoadTimestamp) - where to resume from</li>
 *   <li>Maximum query period (maxQueryPeriodSeconds) - limit window size</li>
 *   <li>Current time - don't query into the future</li>
 *   <li>Default lookback - for first run when lastLoadTimestamp is null</li>
 * </ul>
 *
 * <p><b>Example Scenarios:</b>
 *
 * <p><b>First Run (lastLoadTimestamp = null):</b>
 * <pre>
 * Loader has never run before
 * fromTime: now - defaultLookbackHours (e.g., 24 hours ago)
 * toTime: now
 * </pre>
 *
 * <p><b>Catching Up (30 days behind, maxQueryPeriod = 5 days):</b>
 * <pre>
 * lastLoadTimestamp: 30 days ago
 * fromTime: 30 days ago
 * toTime: 30 days ago + 5 days (respects limit)
 * Next run will continue from this toTime
 * </pre>
 *
 * <p><b>Normal Operation (up to date):</b>
 * <pre>
 * lastLoadTimestamp: 5 minutes ago
 * fromTime: 5 minutes ago
 * toTime: now
 * </pre>
 *
 * <p><b>Future Timestamp (clock skew):</b>
 * <pre>
 * lastLoadTimestamp: 1 hour in the future (shouldn't happen, but handle it)
 * fromTime: now - defaultLookbackHours
 * toTime: now
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public interface TimeWindowCalculator {

  /**
   * Calculates the time window for the next loader execution.
   *
   * <p>Algorithm:
   * <ol>
   *   <li>Determine fromTime:
   *     <ul>
   *       <li>If lastLoadTimestamp is null: now - defaultLookbackHours</li>
   *       <li>If lastLoadTimestamp is in the future: now - defaultLookbackHours</li>
   *       <li>Otherwise: lastLoadTimestamp</li>
   *     </ul>
   *   </li>
   *   <li>Calculate ideal toTime: fromTime + maxQueryPeriodSeconds</li>
   *   <li>Cap toTime at current time (don't query future data)</li>
   *   <li>Validate: fromTime must be before toTime</li>
   * </ol>
   *
   * @param loader the loader to calculate window for
   * @return time window for query execution
   * @throws IllegalArgumentException if loader is null or has invalid configuration
   */
  TimeWindow calculateWindow(Loader loader);
}
