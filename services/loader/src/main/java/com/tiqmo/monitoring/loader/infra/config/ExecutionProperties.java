package com.tiqmo.monitoring.loader.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for loader execution.
 *
 * <p>Binds to {@code loader.execution} in application.yaml.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "loader.execution")
public class ExecutionProperties {

  /**
   * Default lookback period for first run (when lastLoadTimestamp is null).
   * Also used when lastLoadTimestamp is in the future (clock skew).
   */
  private int defaultLookbackHours = 24;

  /**
   * Thread pool size for concurrent loader executions.
   * Default: 10 concurrent loaders.
   */
  private int threadPoolSize = 10;

  /**
   * Execution timeout in hours.
   * Loaders exceeding this will be forcefully terminated.
   * Default: 2 hours (same as stale lock threshold).
   */
  private int executionTimeoutHours = 2;
}
