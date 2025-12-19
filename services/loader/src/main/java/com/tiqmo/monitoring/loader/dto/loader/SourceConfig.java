// src/main/java/com/tiqmo/monitoring/loader/service/dto/SourceConfig.java
package com.tiqmo.monitoring.loader.dto.loader;

import lombok.*;

@Getter @Builder
@AllArgsConstructor
public class SourceConfig {
  private final String dbCode;
  private final String host;
  private final Integer port;
  private final String dbName;
  private final String user;
  private final String pass;  // consider encrypting at rest later
}
