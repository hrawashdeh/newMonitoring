package com.tiqmo.monitoring.loader.service.execution;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultTimeWindowCalculator.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
class DefaultTimeWindowCalculatorTest {

  private TimeWindowCalculator calculator;
  private Loader testLoader;

  @BeforeEach
  void setUp() {
    // Default lookback: 24 hours
    calculator = new DefaultTimeWindowCalculator(24);

    testLoader = Loader.builder()
        .id(1L)
        .loaderCode("TEST_LOADER")
        .maxQueryPeriodSeconds(432000)  // 5 days
        .build();
  }

  @Test
  void testCalculateWindow_FirstRun_NullLastLoadTimestamp() {
    // Arrange
    testLoader.setLastLoadTimestamp(null);
    Instant before = Instant.now().minusSeconds(24 * 3600 + 10);
    Instant after = Instant.now().minusSeconds(24 * 3600 - 10);

    // Act
    TimeWindow window = calculator.calculateWindow(testLoader);

    // Assert
    assertNotNull(window);
    assertNotNull(window.fromTime());
    assertNotNull(window.toTime());

    // fromTime should be approximately 24 hours ago
    assertTrue(window.fromTime().isAfter(before),
        "fromTime should be after " + before + ", got " + window.fromTime());
    assertTrue(window.fromTime().isBefore(after),
        "fromTime should be before " + after + ", got " + window.fromTime());

    // toTime should be approximately now
    Instant now = Instant.now();
    assertTrue(window.toTime().isAfter(now.minusSeconds(5)),
        "toTime should be approximately now");
    assertTrue(window.toTime().isBefore(now.plusSeconds(5)),
        "toTime should be approximately now");

    // Duration should be approximately 24 hours
    long durationHours = window.getDurationSeconds() / 3600;
    assertTrue(durationHours >= 23 && durationHours <= 25,
        "Duration should be approximately 24 hours, got " + durationHours);
  }

  @Test
  void testCalculateWindow_CatchingUp_30DaysBehind() {
    // Arrange - loader is 30 days behind
    Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
    testLoader.setLastLoadTimestamp(thirtyDaysAgo);

    // Act
    TimeWindow window = calculator.calculateWindow(testLoader);

    // Assert
    assertEquals(thirtyDaysAgo, window.fromTime(),
        "fromTime should be lastLoadTimestamp (30 days ago)");

    // toTime should be fromTime + maxQueryPeriod (5 days)
    Instant expectedToTime = thirtyDaysAgo.plusSeconds(testLoader.getMaxQueryPeriodSeconds());
    assertEquals(expectedToTime, window.toTime(),
        "toTime should be fromTime + maxQueryPeriod");

    // Duration should be exactly 5 days (maxQueryPeriod)
    long durationDays = window.getDurationSeconds() / (3600 * 24);
    assertEquals(5, durationDays, "Duration should be 5 days (maxQueryPeriod)");
  }

  @Test
  void testCalculateWindow_CatchingUp_MultipleRuns() {
    // Arrange - simulate multiple runs to catch up
    Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
    testLoader.setLastLoadTimestamp(thirtyDaysAgo);

    // Run 1
    TimeWindow window1 = calculator.calculateWindow(testLoader);
    assertEquals(thirtyDaysAgo, window1.fromTime());

    // Simulate successful execution - update lastLoadTimestamp
    testLoader.setLastLoadTimestamp(window1.toTime());

    // Run 2 - should continue from where Run 1 ended
    TimeWindow window2 = calculator.calculateWindow(testLoader);
    assertEquals(window1.toTime(), window2.fromTime(),
        "Run 2 should start from where Run 1 ended");

    // Run 2 should also be 5 days (still catching up)
    long duration2Days = window2.getDurationSeconds() / (3600 * 24);
    assertEquals(5, duration2Days);
  }

  @Test
  void testCalculateWindow_NormalOperation_UpToDate() {
    // Arrange - loader ran 5 minutes ago
    Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
    testLoader.setLastLoadTimestamp(fiveMinutesAgo);

    // Act
    TimeWindow window = calculator.calculateWindow(testLoader);

    // Assert
    assertEquals(fiveMinutesAgo, window.fromTime(),
        "fromTime should be lastLoadTimestamp");

    // toTime should be approximately now (capped by current time)
    Instant now = Instant.now();
    assertTrue(window.toTime().isAfter(now.minusSeconds(5)),
        "toTime should be approximately now");
    assertTrue(window.toTime().isBefore(now.plusSeconds(5)),
        "toTime should be approximately now");

    // Duration should be approximately 5 minutes
    long durationMinutes = window.getDurationSeconds() / 60;
    assertTrue(durationMinutes >= 4 && durationMinutes <= 6,
        "Duration should be approximately 5 minutes, got " + durationMinutes);
  }

  @Test
  void testCalculateWindow_FutureTimestamp_ClockSkew() {
    // Arrange - lastLoadTimestamp is 1 hour in the future (shouldn't happen, but handle it)
    Instant oneHourInFuture = Instant.now().plus(1, ChronoUnit.HOURS);
    testLoader.setLastLoadTimestamp(oneHourInFuture);

    // Act
    TimeWindow window = calculator.calculateWindow(testLoader);

    // Assert
    // Should use default lookback (24 hours ago) instead of future timestamp
    Instant now = Instant.now();
    Instant expectedFromTime = now.minusSeconds(24 * 3600);

    // fromTime should be approximately 24 hours ago
    assertTrue(window.fromTime().isBefore(expectedFromTime.plusSeconds(10)),
        "fromTime should be approximately 24 hours ago");
    assertTrue(window.fromTime().isAfter(expectedFromTime.minusSeconds(10)),
        "fromTime should be approximately 24 hours ago");

    // toTime should be approximately now
    assertTrue(window.toTime().isAfter(now.minusSeconds(5)),
        "toTime should be approximately now");
    assertTrue(window.toTime().isBefore(now.plusSeconds(5)),
        "toTime should be approximately now");
  }

  @Test
  void testCalculateWindow_ShortMaxQueryPeriod() {
    // Arrange - maxQueryPeriod is only 1 hour
    testLoader.setMaxQueryPeriodSeconds(3600);
    Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
    testLoader.setLastLoadTimestamp(oneDayAgo);

    // Act
    TimeWindow window = calculator.calculateWindow(testLoader);

    // Assert
    assertEquals(oneDayAgo, window.fromTime());

    // toTime should be fromTime + 1 hour (maxQueryPeriod)
    Instant expectedToTime = oneDayAgo.plusSeconds(3600);
    assertEquals(expectedToTime, window.toTime());

    // Duration should be 1 hour
    assertEquals(3600, window.getDurationSeconds());
  }

  @Test
  void testCalculateWindow_VeryRecentExecution() {
    // Arrange - loader ran 10 seconds ago
    Instant tenSecondsAgo = Instant.now().minusSeconds(10);
    testLoader.setLastLoadTimestamp(tenSecondsAgo);

    // Act
    TimeWindow window = calculator.calculateWindow(testLoader);

    // Assert
    assertEquals(tenSecondsAgo, window.fromTime());

    // toTime should be approximately now
    Instant now = Instant.now();
    assertTrue(window.toTime().isAfter(now.minusSeconds(5)),
        "toTime should be approximately now");
    assertTrue(window.toTime().isBefore(now.plusSeconds(5)),
        "toTime should be approximately now");

    // Duration should be approximately 10 seconds
    long durationSeconds = window.getDurationSeconds();
    assertTrue(durationSeconds >= 9 && durationSeconds <= 11,
        "Duration should be approximately 10 seconds, got " + durationSeconds);
  }

  @Test
  void testCalculateWindow_MaxQueryPeriodLargerThanTimeElapsed() {
    // Arrange - maxQueryPeriod is 5 days, but only 1 day has elapsed
    Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
    testLoader.setLastLoadTimestamp(oneDayAgo);
    testLoader.setMaxQueryPeriodSeconds(432000);  // 5 days

    // Act
    TimeWindow window = calculator.calculateWindow(testLoader);

    // Assert
    assertEquals(oneDayAgo, window.fromTime());

    // toTime should be capped at now (not fromTime + 5 days)
    Instant now = Instant.now();
    assertTrue(window.toTime().isAfter(now.minusSeconds(5)),
        "toTime should be approximately now");
    assertTrue(window.toTime().isBefore(now.plusSeconds(5)),
        "toTime should be approximately now");

    // Duration should be approximately 1 day (time elapsed, not max period)
    long durationHours = window.getDurationSeconds() / 3600;
    assertTrue(durationHours >= 23 && durationHours <= 25,
        "Duration should be approximately 24 hours, got " + durationHours);
  }

  @Test
  void testCalculateWindow_NullLoader_ThrowsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> calculator.calculateWindow(null),
        "Should throw exception for null loader");
  }

  @Test
  void testCalculateWindow_NullMaxQueryPeriod_ThrowsException() {
    // Arrange
    testLoader.setMaxQueryPeriodSeconds(null);

    // Act & Assert
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> calculator.calculateWindow(testLoader)
    );

    assertTrue(exception.getMessage().contains("maxQueryPeriodSeconds"),
        "Exception message should mention maxQueryPeriodSeconds");
  }

  @Test
  void testCalculateWindow_ZeroMaxQueryPeriod_ThrowsException() {
    // Arrange
    testLoader.setMaxQueryPeriodSeconds(0);

    // Act & Assert
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> calculator.calculateWindow(testLoader)
    );

    assertTrue(exception.getMessage().contains("maxQueryPeriodSeconds"),
        "Exception message should mention maxQueryPeriodSeconds");
    assertTrue(exception.getMessage().contains("positive"),
        "Exception message should mention 'positive'");
  }

  @Test
  void testCalculateWindow_NegativeMaxQueryPeriod_ThrowsException() {
    // Arrange
    testLoader.setMaxQueryPeriodSeconds(-3600);

    // Act & Assert
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> calculator.calculateWindow(testLoader)
    );

    assertTrue(exception.getMessage().contains("maxQueryPeriodSeconds"));
    assertTrue(exception.getMessage().contains("positive"));
  }

  @Test
  void testConstructor_ZeroDefaultLookback_ThrowsException() {
    // Act & Assert
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> new DefaultTimeWindowCalculator(0)
    );

    assertTrue(exception.getMessage().contains("positive"),
        "Exception message should mention 'positive'");
  }

  @Test
  void testConstructor_NegativeDefaultLookback_ThrowsException() {
    // Act & Assert
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> new DefaultTimeWindowCalculator(-24)
    );

    assertTrue(exception.getMessage().contains("positive"));
  }

  @Test
  void testConstructor_CustomDefaultLookback() {
    // Arrange & Act
    TimeWindowCalculator customCalculator = new DefaultTimeWindowCalculator(48);
    testLoader.setLastLoadTimestamp(null);

    TimeWindow window = customCalculator.calculateWindow(testLoader);

    // Assert - should use 48 hours lookback instead of default 24
    long durationHours = window.getDurationSeconds() / 3600;
    assertTrue(durationHours >= 47 && durationHours <= 49,
        "Duration should be approximately 48 hours, got " + durationHours);
  }

  @Test
  void testTimeWindow_ValidConstruction() {
    // Arrange
    Instant from = Instant.now().minusSeconds(3600);
    Instant to = Instant.now();

    // Act
    TimeWindow window = new TimeWindow(from, to);

    // Assert
    assertEquals(from, window.fromTime());
    assertEquals(to, window.toTime());
    assertEquals(3600, window.getDurationSeconds());
  }

  @Test
  void testTimeWindow_NullFromTime_ThrowsException() {
    // Arrange
    Instant to = Instant.now();

    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> new TimeWindow(null, to),
        "Should throw exception for null fromTime");
  }

  @Test
  void testTimeWindow_NullToTime_ThrowsException() {
    // Arrange
    Instant from = Instant.now();

    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> new TimeWindow(from, null),
        "Should throw exception for null toTime");
  }

  @Test
  void testTimeWindow_FromTimeEqualsToTime_ThrowsException() {
    // Arrange
    Instant time = Instant.now();

    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> new TimeWindow(time, time),
        "Should throw exception when fromTime equals toTime");
  }

  @Test
  void testTimeWindow_FromTimeAfterToTime_ThrowsException() {
    // Arrange
    Instant from = Instant.now();
    Instant to = from.minusSeconds(3600);

    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> new TimeWindow(from, to),
        "Should throw exception when fromTime is after toTime");
  }

  @Test
  void testCalculateWindow_MultipleConsecutiveRuns() {
    // Arrange - simulate 6 consecutive runs over 30 days (5 days each)
    Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
    testLoader.setLastLoadTimestamp(thirtyDaysAgo);

    Instant lastToTime = thirtyDaysAgo;

    // Act & Assert - 6 runs to catch up (30 days / 5 days per run)
    for (int i = 1; i <= 6; i++) {
      TimeWindow window = calculator.calculateWindow(testLoader);

      assertEquals(lastToTime, window.fromTime(),
          "Run " + i + " should start from previous run's toTime");

      long durationDays = window.getDurationSeconds() / (3600 * 24);
      assertEquals(5, durationDays,
          "Run " + i + " should have 5-day duration");

      // Simulate successful execution
      testLoader.setLastLoadTimestamp(window.toTime());
      lastToTime = window.toTime();
    }

    // After 6 runs (30 days), should be caught up
    TimeWindow finalWindow = calculator.calculateWindow(testLoader);
    Instant now = Instant.now();
    assertTrue(finalWindow.toTime().isAfter(now.minusSeconds(10)),
        "After catching up, should be querying up to now");
  }
}
