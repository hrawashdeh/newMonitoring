package com.tiqmo.monitoring.initializer.domain.enums;

/**
 * Loader execution status.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public enum LoadStatus {
  /**
   * Ready to run (idle state).
   */
  IDLE,

  /**
   * Currently executing a load.
   */
  RUNNING,

  /**
   * Last execution failed.
   * Auto-recovers to IDLE after 20 minutes.
   */
  FAILED,

  /**
   * Manually paused (won't execute until resumed).
   */
  PAUSED
}
