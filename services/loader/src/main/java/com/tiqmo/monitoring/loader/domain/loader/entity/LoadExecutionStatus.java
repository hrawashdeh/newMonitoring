package com.tiqmo.monitoring.loader.domain.loader.entity;

/**
 * Status of a single load execution (history record).
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public enum LoadExecutionStatus {
  /**
   * Load is currently running.
   */
  RUNNING,

  /**
   * Load completed successfully.
   */
  SUCCESS,

  /**
   * Load failed with errors.
   */
  FAILED,

  /**
   * Load partially succeeded (some data loaded, some errors).
   */
  PARTIAL
}
