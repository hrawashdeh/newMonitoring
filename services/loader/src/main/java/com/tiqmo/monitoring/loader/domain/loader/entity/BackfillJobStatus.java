package com.tiqmo.monitoring.loader.domain.loader.entity;

/**
 * Status of a backfill job.
 *
 * <p>Backfill jobs can be queued (PENDING), actively executing (RUNNING),
 * completed successfully (SUCCESS), failed (FAILED), or cancelled by user (CANCELLED).
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public enum BackfillJobStatus {
  /**
   * Job is queued and waiting to execute.
   */
  PENDING,

  /**
   * Job is currently executing.
   */
  RUNNING,

  /**
   * Job completed successfully.
   */
  SUCCESS,

  /**
   * Job failed with errors.
   */
  FAILED,

  /**
   * Job was cancelled by user before completion.
   */
  CANCELLED
}
