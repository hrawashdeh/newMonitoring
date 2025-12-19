package com.tiqmo.monitoring.loader.service.locking;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Represents an acquired execution lock.
 *
 * <p>This is a lightweight DTO returned by {@link LockManager#tryAcquireLock(com.tiqmo.monitoring.loader.domain.loader.entity.Loader)}.
 * Contains only the essential information needed to release the lock.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Data
@Builder
public class LoaderLock {

  /**
   * Unique lock identifier (UUID).
   * Used to release the lock via {@link LockManager#releaseLock(String)}.
   */
  private String lockId;

  /**
   * Loader code this lock is for.
   */
  private String loaderCode;

  /**
   * Replica/pod name that acquired this lock.
   */
  private String replicaName;

  /**
   * When the lock was acquired.
   */
  private Instant acquiredAt;
}
