package com.tiqmo.monitoring.loader.service.execution;

import com.tiqmo.monitoring.loader.domain.loader.entity.LoadHistory;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;

/**
 * Service for executing ETL loaders and tracking execution history.
 *
 * <p>This service orchestrates the complete loader execution lifecycle:
 * <ol>
 *   <li>Record execution start (LoadHistory with RUNNING status)</li>
 *   <li>Execute loader query against source database</li>
 *   <li>Transform and ingest data into signals_history</li>
 *   <li>Record execution result (SUCCESS/FAILED/PARTIAL)</li>
 *   <li>Update loader runtime state (lastLoadTimestamp, loadStatus)</li>
 * </ol>
 *
 * <p><b>Replica Tracking:</b> All executions record the pod/replica name
 * for distributed debugging.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public interface LoadExecutorService {

  /**
   * Executes a loader and records execution history.
   *
   * <p>This method:
   * <ul>
   *   <li>Creates LoadHistory record with RUNNING status</li>
   *   <li>Executes loader query (stub in this round)</li>
   *   <li>Records execution results (duration, records, errors)</li>
   *   <li>Updates LoadHistory to SUCCESS/FAILED/PARTIAL</li>
   * </ul>
   *
   * <p><b>Exception Handling:</b>
   * <ul>
   *   <li>All exceptions caught and recorded in LoadHistory</li>
   *   <li>Loader status updated to FAILED on exception</li>
   *   <li>Stack traces captured for debugging</li>
   * </ul>
   *
   * @param loader the loader to execute (must not be null)
   * @return execution history record
   * @throws IllegalArgumentException if loader is null or invalid
   */
  LoadHistory executeLoader(Loader loader);

  /**
   * Executes a loader by code.
   *
   * <p>Convenience method that looks up loader by code and executes it.
   *
   * @param loaderCode the loader code
   * @return execution history record
   * @throws IllegalArgumentException if loader not found
   */
  LoadHistory executeLoader(String loaderCode);
}
