package com.tiqmo.monitoring.loader.service.execution;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Default implementation of TimeWindowCalculator.
 *
 * <p>Calculates query time windows based on loader configuration and current state.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
public class DefaultTimeWindowCalculator implements TimeWindowCalculator {

  private final long defaultLookbackSeconds;

  /**
   * Constructor with configuration injection.
   *
   * @param defaultLookbackHours default lookback period in hours
   */
  public DefaultTimeWindowCalculator(
      @Value("${loader.execution.default-lookback-hours:24}") int defaultLookbackHours) {
    if (defaultLookbackHours <= 0) {
      throw new IllegalArgumentException(
          "Default lookback hours must be positive, got: " + defaultLookbackHours);
    }
    this.defaultLookbackSeconds = defaultLookbackHours * 3600L;
    log.info("TimeWindowCalculator initialized with default lookback: {} hours",
        defaultLookbackHours);
  }

  @Override
  public TimeWindow calculateWindow(Loader loader) {
    if (loader == null) {
      throw new IllegalArgumentException("Loader cannot be null");
    }

    if (loader.getMaxQueryPeriodSeconds() == null || loader.getMaxQueryPeriodSeconds() <= 0) {
      throw new IllegalArgumentException(
          "Loader maxQueryPeriodSeconds must be positive, got: " +
              loader.getMaxQueryPeriodSeconds());
    }

    Instant now = Instant.now();
    Instant lastLoadTimestamp = loader.getLastLoadTimestamp();

    // 1. Determine fromTime
    Instant fromTime = determineFromTime(lastLoadTimestamp, now);

    // 2. Calculate ideal toTime based on max query period
    long maxQueryPeriodSeconds = loader.getMaxQueryPeriodSeconds();
    Instant idealToTime = fromTime.plusSeconds(maxQueryPeriodSeconds);

    // 3. Cap toTime at current time (don't query future data)
    Instant toTime = idealToTime.isAfter(now) ? now : idealToTime;

    // 4. Validate: fromTime must be before toTime
    if (!fromTime.isBefore(toTime)) {
      log.warn("Invalid time window for loader {}: fromTime={} >= toTime={}, " +
              "using minimal 1-second window",
          loader.getLoaderCode(), fromTime, toTime);
      toTime = fromTime.plusSeconds(1);
    }

    long durationSeconds = toTime.getEpochSecond() - fromTime.getEpochSecond();
    log.debug("Calculated time window for loader {}: from={}, to={}, duration={}s",
        loader.getLoaderCode(), fromTime, toTime, durationSeconds);

    return new TimeWindow(fromTime, toTime);
  }

  /**
   * Determines the fromTime for query window.
   *
   * <p>Logic:
   * <ul>
   *   <li>If lastLoadTimestamp is null (first run): now - defaultLookbackSeconds</li>
   *   <li>If lastLoadTimestamp is in the future (clock skew): now - defaultLookbackSeconds</li>
   *   <li>Otherwise: lastLoadTimestamp (resume from last point)</li>
   * </ul>
   *
   * @param lastLoadTimestamp last load timestamp from loader
   * @param now current timestamp
   * @return fromTime for query window
   */
  private Instant determineFromTime(Instant lastLoadTimestamp, Instant now) {
    // First run: never loaded before
    if (lastLoadTimestamp == null) {
      Instant fromTime = now.minusSeconds(defaultLookbackSeconds);
      log.debug("First run detected (lastLoadTimestamp=null), using default lookback: {}",
          fromTime);
      return fromTime;
    }

    // Clock skew: lastLoadTimestamp is in the future
    if (lastLoadTimestamp.isAfter(now)) {
      Instant fromTime = now.minusSeconds(defaultLookbackSeconds);
      log.warn("Clock skew detected: lastLoadTimestamp={} is after now={}, " +
              "using default lookback: {}",
          lastLoadTimestamp, now, fromTime);
      return fromTime;
    }

    // Normal case: resume from last load point
    return lastLoadTimestamp;
  }
}
