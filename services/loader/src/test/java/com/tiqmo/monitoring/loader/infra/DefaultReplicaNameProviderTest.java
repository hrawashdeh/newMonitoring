package com.tiqmo.monitoring.loader.infra;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultReplicaNameProvider.
 *
 * <p>Tests replica name detection logic from various sources:
 * configuration, environment variables, and network hostname.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
class DefaultReplicaNameProviderTest {

  @Test
  void testConfiguredName_TakesPriority() {
    String configuredName = "my-custom-replica";
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(configuredName);

    assertEquals(configuredName, provider.getReplicaName(),
        "Configured name should take priority over all other sources");
  }

  @Test
  void testConfiguredName_TrimsWhitespace() {
    String configuredName = "  my-replica  ";
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(configuredName);

    assertEquals("my-replica", provider.getReplicaName(),
        "Configured name should be trimmed");
  }

  @Test
  void testConfiguredName_BlankIgnored() {
    String configuredName = "   ";
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(configuredName);

    // Should fall back to HOSTNAME or other detection
    assertNotNull(provider.getReplicaName());
    assertNotEquals("", provider.getReplicaName());
  }

  @Test
  void testConfiguredName_NullIgnored() {
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(null);

    // Should fall back to HOSTNAME or other detection
    assertNotNull(provider.getReplicaName());
  }

  @Test
  void testFallsBackToHostname_WhenNoConfiguration() {
    // When no configuration provided, should use HOSTNAME env var or network hostname
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(null);

    String replicaName = provider.getReplicaName();
    assertNotNull(replicaName, "Replica name should never be null");
    assertFalse(replicaName.isBlank(), "Replica name should not be blank");
  }

  @Test
  void testReplicaName_ConsistentAcrossCalls() {
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(null);

    String first = provider.getReplicaName();
    String second = provider.getReplicaName();

    assertEquals(first, second,
        "Replica name should be consistent across multiple calls");
  }

  @Test
  void testReplicaName_DetectedOnStartup() {
    // Replica name should be detected in constructor, not on every call
    String configuredName = "test-replica";
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(configuredName);

    String replicaName = provider.getReplicaName();
    assertEquals("test-replica", replicaName);

    // Verify it's a stored value (not re-detecting)
    String storedValue = (String) ReflectionTestUtils.getField(provider, "replicaName");
    assertEquals(configuredName, storedValue);
  }

  @Test
  void testKubernetesScenario_PodName() {
    // Simulate Kubernetes environment where HOSTNAME is set to pod name
    // Note: In real K8s, HOSTNAME would be something like "loader-deployment-7d9f8c6b5-xk4j7"
    // This test verifies the logic would use it if configured

    String podName = "loader-deployment-7d9f8c6b5-xk4j7";
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(podName);

    assertEquals(podName, provider.getReplicaName());
  }

  @Test
  void testLocalDevelopment_ConfiguredName() {
    // Local development scenario: developer sets custom replica name
    String devName = "dev-laptop-john";
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(devName);

    assertEquals(devName, provider.getReplicaName());
  }

  @Test
  void testGetReplicaName_NeverReturnsNull() {
    DefaultReplicaNameProvider provider1 = new DefaultReplicaNameProvider(null);
    assertNotNull(provider1.getReplicaName());

    DefaultReplicaNameProvider provider2 = new DefaultReplicaNameProvider("");
    assertNotNull(provider2.getReplicaName());

    DefaultReplicaNameProvider provider3 = new DefaultReplicaNameProvider("   ");
    assertNotNull(provider3.getReplicaName());
  }

  @Test
  void testGetReplicaName_NeverReturnsBlank() {
    DefaultReplicaNameProvider provider1 = new DefaultReplicaNameProvider(null);
    assertFalse(provider1.getReplicaName().isBlank());

    DefaultReplicaNameProvider provider2 = new DefaultReplicaNameProvider("");
    assertFalse(provider2.getReplicaName().isBlank());

    DefaultReplicaNameProvider provider3 = new DefaultReplicaNameProvider("   ");
    assertFalse(provider3.getReplicaName().isBlank());
  }

  @Test
  void testReplicaName_ReasonableLength() {
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(null);
    String replicaName = provider.getReplicaName();

    // Kubernetes pod names are max 63 characters, but network hostnames can be longer
    // Verify we get a reasonable value
    assertTrue(replicaName.length() > 0, "Replica name should not be empty");
    assertTrue(replicaName.length() < 256, "Replica name should be reasonably short");
  }

  @Test
  void testSpecialCharacters_Preserved() {
    String nameWithDashes = "loader-pod-123";
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(nameWithDashes);

    assertEquals(nameWithDashes, provider.getReplicaName(),
        "Dashes and numbers should be preserved");
  }

  @Test
  void testLongReplicaName_Accepted() {
    String longName = "a".repeat(63); // Max Kubernetes pod name length
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(longName);

    assertEquals(longName, provider.getReplicaName(),
        "Long replica names should be accepted");
  }

  @Test
  void testNetworkHostname_UsedAsFallback() throws UnknownHostException {
    // When no configuration provided, should eventually fall back to network hostname
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider(null);

    String replicaName = provider.getReplicaName();
    String networkHostname = InetAddress.getLocalHost().getHostName();

    // Replica name should be either HOSTNAME env var or network hostname
    // We can't guarantee which in test environment, but it should be non-empty
    assertNotNull(replicaName);
    assertFalse(replicaName.isBlank());
  }

  @Test
  void testMultipleInstances_IndependentDetection() {
    DefaultReplicaNameProvider provider1 = new DefaultReplicaNameProvider("replica-1");
    DefaultReplicaNameProvider provider2 = new DefaultReplicaNameProvider("replica-2");

    assertEquals("replica-1", provider1.getReplicaName());
    assertEquals("replica-2", provider2.getReplicaName());

    // Verify they don't interfere with each other
    assertEquals("replica-1", provider1.getReplicaName());
  }

  @Test
  void testThreadSafety_ConsistentValue() throws InterruptedException {
    DefaultReplicaNameProvider provider = new DefaultReplicaNameProvider("test-replica");

    // Simulate multiple threads accessing replica name
    String[] results = new String[10];
    Thread[] threads = new Thread[10];

    for (int i = 0; i < 10; i++) {
      final int index = i;
      threads[i] = new Thread(() -> {
        results[index] = provider.getReplicaName();
      });
      threads[i].start();
    }

    // Wait for all threads
    for (Thread thread : threads) {
      thread.join();
    }

    // All threads should get same value
    for (String result : results) {
      assertEquals("test-replica", result);
    }
  }
}
