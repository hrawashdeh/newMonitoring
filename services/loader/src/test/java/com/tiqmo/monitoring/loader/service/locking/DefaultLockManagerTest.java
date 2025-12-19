package com.tiqmo.monitoring.loader.service.locking;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderExecutionLock;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderExecutionLockRepository;
import com.tiqmo.monitoring.loader.infra.ReplicaNameProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DefaultLockManager.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class DefaultLockManagerTest {

  @Mock
  private LoaderExecutionLockRepository lockRepository;

  @Mock
  private ReplicaNameProvider replicaNameProvider;

  @InjectMocks
  private DefaultLockManager lockManager;

  private Loader testLoader;

  @BeforeEach
  void setUp() {
    testLoader = Loader.builder()
        .id(1L)
        .loaderCode("TEST_LOADER")
        .maxParallelExecutions(2)  // Allow 2 parallel executions
        .build();

    when(replicaNameProvider.getReplicaName()).thenReturn("test-replica");
  }

  @Test
  void testTryAcquireLock_Success() {
    // Arrange
    when(lockRepository.countByLoaderCodeAndReleased("TEST_LOADER", false)).thenReturn(0L);
    when(lockRepository.countByReleased(false)).thenReturn(50L);  // Under global limit

    // Act
    Optional<LoaderLock> result = lockManager.tryAcquireLock(testLoader);

    // Assert
    assertTrue(result.isPresent(), "Lock should be acquired");
    assertEquals("TEST_LOADER", result.get().getLoaderCode());
    assertEquals("test-replica", result.get().getReplicaName());
    assertNotNull(result.get().getLockId());
    assertNotNull(result.get().getAcquiredAt());

    verify(lockRepository).save(any(LoaderExecutionLock.class));
  }

  @Test
  void testTryAcquireLock_CreatesLockEntity() {
    // Arrange
    when(lockRepository.countByLoaderCodeAndReleased("TEST_LOADER", false)).thenReturn(0L);
    when(lockRepository.countByReleased(false)).thenReturn(50L);

    // Act
    lockManager.tryAcquireLock(testLoader);

    // Assert
    ArgumentCaptor<LoaderExecutionLock> captor = ArgumentCaptor.forClass(LoaderExecutionLock.class);
    verify(lockRepository).save(captor.capture());

    LoaderExecutionLock savedLock = captor.getValue();
    assertEquals("TEST_LOADER", savedLock.getLoaderCode());
    assertEquals("test-replica", savedLock.getReplicaName());
    assertFalse(savedLock.getReleased());
    assertNotNull(savedLock.getLockId());
    assertNotNull(savedLock.getAcquiredAt());
  }

  @Test
  void testTryAcquireLock_PerLoaderLimitReached() {
    // Arrange
    when(lockRepository.countByLoaderCodeAndReleased("TEST_LOADER", false))
        .thenReturn(2L);  // Already at max (2)
    when(lockRepository.countByReleased(false)).thenReturn(50L);

    // Act
    Optional<LoaderLock> result = lockManager.tryAcquireLock(testLoader);

    // Assert
    assertFalse(result.isPresent(), "Lock should not be acquired (per-loader limit reached)");
    verify(lockRepository, never()).save(any());
  }

  @Test
  void testTryAcquireLock_GlobalLimitReached() {
    // Arrange
    when(lockRepository.countByLoaderCodeAndReleased("TEST_LOADER", false)).thenReturn(0L);
    when(lockRepository.countByReleased(false)).thenReturn(100L);  // At global limit

    // Act
    Optional<LoaderLock> result = lockManager.tryAcquireLock(testLoader);

    // Assert
    assertFalse(result.isPresent(), "Lock should not be acquired (global limit reached)");
    verify(lockRepository, never()).save(any());
  }

  @Test
  void testTryAcquireLock_NullLoader_ThrowsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> lockManager.tryAcquireLock(null),
        "Should throw exception for null loader");
  }

  @Test
  void testReleaseLock_ByLockObject() {
    // Arrange
    LoaderLock lock = LoaderLock.builder()
        .lockId("test-lock-id")
        .loaderCode("TEST_LOADER")
        .replicaName("test-replica")
        .acquiredAt(Instant.now())
        .build();

    when(lockRepository.releaseLock(eq("test-lock-id"), any(Instant.class))).thenReturn(1);

    // Act
    lockManager.releaseLock(lock);

    // Assert
    verify(lockRepository).releaseLock(eq("test-lock-id"), any(Instant.class));
  }

  @Test
  void testReleaseLock_ByLockId() {
    // Arrange
    when(lockRepository.releaseLock(eq("test-lock-id"), any(Instant.class))).thenReturn(1);

    // Act
    lockManager.releaseLock("test-lock-id");

    // Assert
    verify(lockRepository).releaseLock(eq("test-lock-id"), any(Instant.class));
  }

  @Test
  void testReleaseLock_NullLock_ThrowsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> lockManager.releaseLock((LoaderLock) null),
        "Should throw exception for null lock");
  }

  @Test
  void testReleaseLock_NullLockId_ThrowsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> lockManager.releaseLock((String) null),
        "Should throw exception for null lock ID");
  }

  @Test
  void testReleaseLock_BlankLockId_ThrowsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class,
        () -> lockManager.releaseLock("   "),
        "Should throw exception for blank lock ID");
  }

  @Test
  void testReleaseLock_AlreadyReleased_LogsWarning() {
    // Arrange
    when(lockRepository.releaseLock(eq("test-lock-id"), any(Instant.class))).thenReturn(0);

    // Act
    lockManager.releaseLock("test-lock-id");

    // Assert - should not throw, just log warning
    verify(lockRepository).releaseLock(eq("test-lock-id"), any(Instant.class));
  }

  @Test
  void testCleanupStaleLocks_Success() {
    // Arrange
    when(lockRepository.cleanupStaleLocks(any(Instant.class), any(Instant.class)))
        .thenReturn(5);

    // Act
    int cleaned = lockManager.cleanupStaleLocks();

    // Assert
    assertEquals(5, cleaned, "Should return number of cleaned locks");
    verify(lockRepository).cleanupStaleLocks(any(Instant.class), any(Instant.class));
  }

  @Test
  void testCleanupStaleLocks_NoneFound() {
    // Arrange
    when(lockRepository.cleanupStaleLocks(any(Instant.class), any(Instant.class)))
        .thenReturn(0);

    // Act
    int cleaned = lockManager.cleanupStaleLocks();

    // Assert
    assertEquals(0, cleaned, "Should return 0 when no stale locks found");
  }

  @Test
  void testCleanupStaleLocks_UsesCorrectThreshold() {
    // Act
    lockManager.cleanupStaleLocks();

    // Assert
    ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> releasedAtCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(lockRepository).cleanupStaleLocks(thresholdCaptor.capture(), releasedAtCaptor.capture());

    Instant threshold = thresholdCaptor.getValue();
    Instant now = Instant.now();

    // Threshold should be ~2 hours ago
    long hoursAgo = (now.toEpochMilli() - threshold.toEpochMilli()) / (3600 * 1000);
    assertTrue(hoursAgo >= 1 && hoursAgo <= 3,
        "Threshold should be approximately 2 hours ago");
  }

  @Test
  void testCountActiveLocks() {
    // Arrange
    when(lockRepository.countByLoaderCodeAndReleased("TEST_LOADER", false))
        .thenReturn(3L);

    // Act
    long count = lockManager.countActiveLocks("TEST_LOADER");

    // Assert
    assertEquals(3L, count);
    verify(lockRepository).countByLoaderCodeAndReleased("TEST_LOADER", false);
  }

  @Test
  void testCountTotalActiveLocks() {
    // Arrange
    when(lockRepository.countByReleased(false)).thenReturn(42L);

    // Act
    long count = lockManager.countTotalActiveLocks();

    // Assert
    assertEquals(42L, count);
    verify(lockRepository).countByReleased(false);
  }

  @Test
  void testAcquireReleaseCycle() {
    // Arrange
    when(lockRepository.countByLoaderCodeAndReleased("TEST_LOADER", false)).thenReturn(0L);
    when(lockRepository.countByReleased(false)).thenReturn(50L);
    when(lockRepository.releaseLock(any(String.class), any(Instant.class))).thenReturn(1);

    // Act
    Optional<LoaderLock> lock = lockManager.tryAcquireLock(testLoader);
    assertTrue(lock.isPresent());

    lockManager.releaseLock(lock.get());

    // Assert
    verify(lockRepository).save(any(LoaderExecutionLock.class));
    verify(lockRepository).releaseLock(any(String.class), any(Instant.class));
  }

  @Test
  void testMultipleAcquire_UpToLimit() {
    // Arrange
    when(lockRepository.countByLoaderCodeAndReleased("TEST_LOADER", false))
        .thenReturn(0L, 1L, 2L);  // Sequential counts
    when(lockRepository.countByReleased(false)).thenReturn(50L);

    // Act
    Optional<LoaderLock> lock1 = lockManager.tryAcquireLock(testLoader);
    Optional<LoaderLock> lock2 = lockManager.tryAcquireLock(testLoader);
    Optional<LoaderLock> lock3 = lockManager.tryAcquireLock(testLoader);

    // Assert
    assertTrue(lock1.isPresent(), "First lock should succeed");
    assertTrue(lock2.isPresent(), "Second lock should succeed");
    assertFalse(lock3.isPresent(), "Third lock should fail (limit=2)");

    verify(lockRepository, times(2)).save(any(LoaderExecutionLock.class));
  }

  @Test
  void testLockIdUniqueness() {
    // Arrange
    when(lockRepository.countByLoaderCodeAndReleased("TEST_LOADER", false)).thenReturn(0L);
    when(lockRepository.countByReleased(false)).thenReturn(50L);

    // Act
    Optional<LoaderLock> lock1 = lockManager.tryAcquireLock(testLoader);
    Optional<LoaderLock> lock2 = lockManager.tryAcquireLock(testLoader);

    // Assert
    assertTrue(lock1.isPresent());
    assertTrue(lock2.isPresent());
    assertNotEquals(lock1.get().getLockId(), lock2.get().getLockId(),
        "Lock IDs should be unique");
  }

  @Test
  void testGlobalLimit_EnforcedBeforeSave() {
    // Arrange
    when(lockRepository.countByLoaderCodeAndReleased("TEST_LOADER", false)).thenReturn(0L);
    when(lockRepository.countByReleased(false)).thenReturn(99L, 100L);

    // Act
    Optional<LoaderLock> lock1 = lockManager.tryAcquireLock(testLoader);
    Optional<LoaderLock> lock2 = lockManager.tryAcquireLock(testLoader);

    // Assert
    assertTrue(lock1.isPresent(), "Should succeed when global count is 99");
    assertFalse(lock2.isPresent(), "Should fail when global count is 100");

    verify(lockRepository, times(1)).save(any(LoaderExecutionLock.class));
  }

  @Test
  void testReplicaName_RecordedInLock() {
    // Arrange
    when(replicaNameProvider.getReplicaName()).thenReturn("pod-123");
    when(lockRepository.countByLoaderCodeAndReleased("TEST_LOADER", false)).thenReturn(0L);
    when(lockRepository.countByReleased(false)).thenReturn(50L);

    // Act
    Optional<LoaderLock> lock = lockManager.tryAcquireLock(testLoader);

    // Assert
    assertTrue(lock.isPresent());
    assertEquals("pod-123", lock.get().getReplicaName());

    ArgumentCaptor<LoaderExecutionLock> captor = ArgumentCaptor.forClass(LoaderExecutionLock.class);
    verify(lockRepository).save(captor.capture());
    assertEquals("pod-123", captor.getValue().getReplicaName());
  }
}
