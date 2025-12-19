package com.tiqmo.monitoring.loader.infra;

/**
 * Provides the replica/pod name for distributed execution tracking.
 *
 * <p>Used to identify which pod/container executed a loader for debugging
 * and tracking in distributed environments.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public interface ReplicaNameProvider {

  /**
   * Gets the current replica/pod name.
   *
   * <p>Detection order:
   * <ol>
   *   <li>Configuration override ({@code loader.replica-name})</li>
   *   <li>Environment variable {@code HOSTNAME} (Kubernetes pod name)</li>
   *   <li>Environment variable {@code COMPUTERNAME} (Windows)</li>
   *   <li>Local hostname via {@code InetAddress.getLocalHost()}</li>
   *   <li>Fallback: "unknown-replica"</li>
   * </ol>
   *
   * @return the replica name (never null)
   */
  String getReplicaName();
}
