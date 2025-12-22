package com.tiqmo.monitoring.initializer.domain.enums;

/**
 * Strategy for handling data beyond last load timestamp.
 *
 * <p>When loader's lastLoadTimestamp is adjusted via API (e.g., moved backwards for reprocessing),
 * this strategy determines how to handle existing data that would be duplicated.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public enum PurgeStrategy {
  /**
   * Raise error if data exists beyond last load timestamp.
   * Prevents accidental data duplication.
   */
  FAIL_ON_DUPLICATE,

  /**
   * Delete existing data beyond last load timestamp before loading.
   * Allows reprocessing historical data.
   */
  PURGE_AND_RELOAD,

  /**
   * Skip duplicate data (keep existing, don't reload).
   * Continues from where it left off.
   */
  SKIP_DUPLICATES
}
