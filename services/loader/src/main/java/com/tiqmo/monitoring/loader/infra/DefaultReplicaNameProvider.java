package com.tiqmo.monitoring.loader.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Default implementation of ReplicaNameProvider.
 *
 * <p>Detects replica name from environment variables (Kubernetes pod name)
 * with fallback to local hostname.
 *
 * <p><b>Detection Priority:</b>
 * <ol>
 *   <li>Configuration: {@code loader.replica-name} in application.yaml</li>
 *   <li>Environment: {@code HOSTNAME} (Kubernetes sets this to pod name)</li>
 *   <li>Environment: {@code COMPUTERNAME} (Windows systems)</li>
 *   <li>Network: {@code InetAddress.getLocalHost().getHostName()}</li>
 *   <li>Fallback: "unknown-replica"</li>
 * </ol>
 *
 * <p><b>Kubernetes Example:</b>
 * <pre>
 * HOSTNAME=loader-deployment-7d9f8c6b5-xk4j7
 * → getReplicaName() returns "loader-deployment-7d9f8c6b5-xk4j7"
 * </pre>
 *
 * <p><b>Local Development Example:</b>
 * <pre>
 * loader.replica-name=dev-laptop
 * → getReplicaName() returns "dev-laptop"
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Component
public class DefaultReplicaNameProvider implements ReplicaNameProvider {

  private final String replicaName;

  /**
   * Constructor that detects replica name on startup.
   *
   * @param configuredReplicaName optional configuration override from {@code loader.replica-name}
   */
  public DefaultReplicaNameProvider(
      @Value("${loader.replica-name:#{null}}") String configuredReplicaName) {

    this.replicaName = detectReplicaName(configuredReplicaName);
    log.info("Replica name detected: {}", replicaName);
  }

  @Override
  public String getReplicaName() {
    return replicaName;
  }

  /**
   * Detects replica name using multiple fallback strategies.
   *
   * @param configuredName optional configuration override
   * @return detected replica name (never null)
   */
  private String detectReplicaName(String configuredName) {
    // 1. Configuration override (highest priority)
    if (configuredName != null && !configuredName.isBlank()) {
      log.debug("Using configured replica name: {}", configuredName);
      return configuredName.trim();
    }

    // 2. Environment variable: HOSTNAME (Kubernetes/Docker)
    String hostname = System.getenv("HOSTNAME");
    if (hostname != null && !hostname.isBlank()) {
      log.debug("Using HOSTNAME environment variable: {}", hostname);
      return hostname.trim();
    }

    // 3. Environment variable: COMPUTERNAME (Windows)
    String computerName = System.getenv("COMPUTERNAME");
    if (computerName != null && !computerName.isBlank()) {
      log.debug("Using COMPUTERNAME environment variable: {}", computerName);
      return computerName.trim();
    }

    // 4. Network hostname (InetAddress)
    try {
      String networkHostname = InetAddress.getLocalHost().getHostName();
      if (networkHostname != null && !networkHostname.isBlank()) {
        log.debug("Using network hostname: {}", networkHostname);
        return networkHostname.trim();
      }
    } catch (UnknownHostException e) {
      log.warn("Failed to get local hostname: {}", e.getMessage());
    }

    // 5. Fallback (should rarely happen)
    log.warn("Could not detect replica name, using fallback: unknown-replica");
    return "unknown-replica";
  }
}
