// src/main/java/com/tiqmo/monitoring/loader/config/SourcePoolProperties.java
package com.tiqmo.monitoring.loader.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sources.mysql.pool")
public class SourcePoolProperties {
  private int minimumIdle = 1;
  private int maximumPoolSize = 5;
  private long idleTimeoutMs = 300_000;
  private long connectionTimeoutMs = 30_000;
  private long leakDetectionThresholdMs = 0;
}
