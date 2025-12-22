package com.tiqmo.monitoring.loader.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for loader execution lock management.
 *
 * <p>Binds to {@code loader.locking} in application.yaml.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "loader.locking")
public class LockingProperties {

  /**
   * Stale lock threshold in hours.
   * Locks older than this are automatically released.
   * Default: 2 hours.
   */
  private int staleLockThresholdHours = 2;

  /**
   * Released lock retention in days.
   * Locks released longer than this are deleted.
   * Default: 7 days (keeps 1 week of historical debugging data).
   */
  private int releasedLockRetentionDays = 7;

  /**
   * Cleanup schedule for released locks (cron expression).
   * Default: daily at 2 AM.
   */
  private String cleanupSchedule = "0 0 2 * * ?";
}
