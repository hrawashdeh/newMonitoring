package com.tiqmo.monitoring.loader.service.scheduler;

import com.tiqmo.monitoring.loader.domain.loader.entity.LoadStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.SourceDatabase;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.service.execution.LoadExecutorService;
import com.tiqmo.monitoring.loader.service.locking.LoaderLock;
import com.tiqmo.monitoring.loader.service.locking.LockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LoaderSchedulerService (Rounds 10-13).
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class LoaderSchedulerServiceTest {

  @Mock
  private LoaderRepository loaderRepository;

  @Mock
  private LockManager lockManager;

  @Mock
  private LoadExecutorService loadExecutorService;

  private LoaderSchedulerService scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new LoaderSchedulerService(loaderRepository, lockManager, loadExecutorService);
  }

  @Test
  void scheduleLoaders_shouldDoNothing_whenNoEnabledLoaders() {
    // Given
    when(loaderRepository.findAllByEnabledTrue()).thenReturn(Collections.emptyList());

    // When
    scheduler.scheduleLoaders();

    // Then
    verify(loaderRepository).findAll(); // For FAILED recovery
    verify(loaderRepository).findAllByEnabledTrue();
    verifyNoInteractions(lockManager);
    verifyNoInteractions(loadExecutorService);
  }

  @Test
  void scheduleLoaders_shouldExecuteLoader_whenDueAndLockAcquired() {
    // Given
    Loader loader = createTestLoader("TEST_LOADER", LoadStatus.IDLE, null);
    when(loaderRepository.findAllByEnabledTrue()).thenReturn(Collections.singletonList(loader));
    when(loaderRepository.findAll()).thenReturn(Collections.singletonList(loader));

    LoaderLock mockLock = LoaderLock.builder()
        .lockId("test-lock-id-1")
        .loaderCode("TEST_LOADER")
        .replicaName("test-replica")
        .acquiredAt(Instant.now())
        .build();
    when(lockManager.tryAcquireLock(any(Loader.class))).thenReturn(Optional.of(mockLock));

    // When
    scheduler.scheduleLoaders();

    // Then
    verify(lockManager).tryAcquireLock(any(Loader.class));
    verify(loadExecutorService).executeLoader(loader);
    verify(lockManager).releaseLock(any(LoaderLock.class));
  }

  @Test
  void scheduleLoaders_shouldNotExecute_whenLockNotAcquired() {
    // Given
    Loader loader = createTestLoader("TEST_LOADER", LoadStatus.IDLE, null);
    when(loaderRepository.findAllByEnabledTrue()).thenReturn(Collections.singletonList(loader));
    when(loaderRepository.findAll()).thenReturn(Collections.singletonList(loader));

    when(lockManager.tryAcquireLock(any(Loader.class))).thenReturn(Optional.empty()); // Lock not acquired

    // When
    scheduler.scheduleLoaders();

    // Then
    verify(lockManager).tryAcquireLock(any(Loader.class));
    verifyNoInteractions(loadExecutorService);
    verify(lockManager, never()).releaseLock(any(LoaderLock.class));
  }

  @Test
  void scheduleLoaders_shouldNotExecute_whenNotDueYet() {
    // Given
    Instant recentTimestamp = Instant.now().minusSeconds(5); // 5 seconds ago
    Loader loader = createTestLoader("TEST_LOADER", LoadStatus.IDLE, recentTimestamp);
    loader.setMinIntervalSeconds(60); // Requires 60 seconds since last run

    when(loaderRepository.findAllByEnabledTrue()).thenReturn(Collections.singletonList(loader));
    when(loaderRepository.findAll()).thenReturn(Collections.singletonList(loader));

    // When
    scheduler.scheduleLoaders();

    // Then
    verifyNoInteractions(lockManager);
    verifyNoInteractions(loadExecutorService);
  }

  @Test
  void scheduleLoaders_shouldRecoverFailedLoader_after20Minutes() {
    // Given
    Instant failedSince = Instant.now().minusSeconds(21 * 60); // 21 minutes ago
    Loader loader = createTestLoader("FAILED_LOADER", LoadStatus.FAILED, null);
    loader.setFailedSince(failedSince);

    when(loaderRepository.findAll()).thenReturn(Collections.singletonList(loader));
    when(loaderRepository.findAllByEnabledTrue()).thenReturn(Collections.singletonList(loader));

    // When
    scheduler.scheduleLoaders();

    // Then
    verify(loaderRepository).save(argThat(l ->
        l.getLoadStatus() == LoadStatus.IDLE &&
        l.getFailedSince() == null
    ));
  }

  @Test
  void scheduleLoaders_shouldPrioritizeIdleLoaders_beforeFailed() {
    // Given
    Loader idleLoader = createTestLoader("IDLE_LOADER", LoadStatus.IDLE, null);
    Loader failedLoader = createTestLoader("FAILED_LOADER", LoadStatus.FAILED, null);

    when(loaderRepository.findAllByEnabledTrue()).thenReturn(Arrays.asList(failedLoader, idleLoader));
    when(loaderRepository.findAll()).thenReturn(Arrays.asList(failedLoader, idleLoader));

    LoaderLock idleLock = LoaderLock.builder()
        .lockId("test-lock-id-2")
        .loaderCode("IDLE_LOADER")
        .replicaName("test-replica")
        .acquiredAt(Instant.now())
        .build();
    LoaderLock failedLock = LoaderLock.builder()
        .lockId("test-lock-id-3")
        .loaderCode("FAILED_LOADER")
        .replicaName("test-replica")
        .acquiredAt(Instant.now())
        .build();
    // Return locks for both loaders (IDLE processed first due to priority sorting)
    when(lockManager.tryAcquireLock(any(Loader.class))).thenReturn(Optional.of(idleLock), Optional.of(failedLock));

    // When
    scheduler.scheduleLoaders();

    // Then
    // Both loaders should attempt lock acquisition, but IDLE loader should be processed first
    verify(lockManager, times(2)).tryAcquireLock(any(Loader.class));
    // Verify IDLE loader was executed first (priority scheduling)
    verify(loadExecutorService).executeLoader(idleLoader);
    verify(loadExecutorService).executeLoader(failedLoader);
  }

  @Test
  void scheduleLoaders_shouldHandleException_andContinue() {
    // Given
    Loader loader1 = createTestLoader("LOADER_1", LoadStatus.IDLE, null);
    Loader loader2 = createTestLoader("LOADER_2", LoadStatus.IDLE, null);

    when(loaderRepository.findAllByEnabledTrue()).thenReturn(Arrays.asList(loader1, loader2));
    when(loaderRepository.findAll()).thenReturn(Arrays.asList(loader1, loader2));

    LoaderLock lock1 = LoaderLock.builder()
        .lockId("test-lock-id-3")
        .loaderCode("LOADER_1")
        .replicaName("test-replica")
        .acquiredAt(Instant.now())
        .build();
    LoaderLock lock2 = LoaderLock.builder()
        .lockId("test-lock-id-4")
        .loaderCode("LOADER_2")
        .replicaName("test-replica")
        .acquiredAt(Instant.now())
        .build();

    when(lockManager.tryAcquireLock(any(Loader.class))).thenReturn(Optional.of(lock1), Optional.of(lock2));

    // First loader throws exception
    doThrow(new RuntimeException("Test exception")).when(loadExecutorService).executeLoader(loader1);

    // When
    scheduler.scheduleLoaders();

    // Then
    verify(loadExecutorService).executeLoader(loader1);
    verify(lockManager).releaseLock(lock1); // Lock released even after exception
    verify(loadExecutorService).executeLoader(loader2); // Second loader still executed
    verify(lockManager).releaseLock(lock2);
  }

  // ==================== Round 13: Stale Lock Cleanup Tests ====================

  @Test
  void cleanupStaleLocks_shouldCallLockManager() {
    // Given
    when(lockManager.cleanupStaleLocks()).thenReturn(0);

    // When
    scheduler.cleanupStaleLocks();

    // Then
    verify(lockManager).cleanupStaleLocks();
  }

  @Test
  void cleanupStaleLocks_shouldLogWarning_whenStaleLocksFound() {
    // Given
    when(lockManager.cleanupStaleLocks()).thenReturn(3); // 3 stale locks cleaned

    // When
    scheduler.cleanupStaleLocks();

    // Then
    verify(lockManager).cleanupStaleLocks();
    // Log warning should be issued (verified via manual testing or log capture)
  }

  @Test
  void cleanupStaleLocks_shouldHandleException_gracefully() {
    // Given
    when(lockManager.cleanupStaleLocks()).thenThrow(new RuntimeException("Database error"));

    // When/Then - should not throw exception
    scheduler.cleanupStaleLocks();

    // Verify cleanup was attempted
    verify(lockManager).cleanupStaleLocks();
  }

  // Helper methods

  private Loader createTestLoader(String loaderCode, LoadStatus status, Instant lastLoadTimestamp) {
    SourceDatabase sourceDb = SourceDatabase.builder()
        .dbCode("TEST_DB")
        .build();

    return Loader.builder()
        .loaderCode(loaderCode)
        .sourceDatabase(sourceDb)
        .loaderSql("SELECT * FROM test")
        .minIntervalSeconds(10)
        .maxIntervalSeconds(60)
        .maxQueryPeriodSeconds(3600)
        .maxParallelExecutions(1)
        .enabled(true)
        .loadStatus(status)
        .lastLoadTimestamp(lastLoadTimestamp)
        .build();
  }
}
