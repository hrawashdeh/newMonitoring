package com.tiqmo.monitoring.loader.service.execution;

import java.time.Instant;

/**
 * Represents a time window for loader query execution.
 *
 * <p>Immutable record containing from/to timestamps for data extraction.
 * Used by {@link TimeWindowCalculator} to determine query time range.
 *
 * @param fromTime Start of query period (inclusive)
 * @param toTime End of query period (exclusive)
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public record TimeWindow(Instant fromTime, Instant toTime) {

  /**
   * Creates a TimeWindow with validation.
   *
   * @throws IllegalArgumentException if fromTime/toTime are null or fromTime >= toTime
   */
  public TimeWindow {
    if (fromTime == null) {
      throw new IllegalArgumentException("fromTime cannot be null");
    }
    if (toTime == null) {
      throw new IllegalArgumentException("toTime cannot be null");
    }
    if (!fromTime.isBefore(toTime)) {
      throw new IllegalArgumentException("fromTime must be before toTime");
    }
  }

  /**
   * Returns duration in seconds.
   */
  public long getDurationSeconds() {
    return toTime.getEpochSecond() - fromTime.getEpochSecond();
  }
}
