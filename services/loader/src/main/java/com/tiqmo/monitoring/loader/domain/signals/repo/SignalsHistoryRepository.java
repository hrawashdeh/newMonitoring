// src/main/java/com/tiqmo/monitoring/loader/domain/signals/repo/SignalsHistoryRepository.java
package com.tiqmo.monitoring.loader.domain.signals.repo;

import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface SignalsHistoryRepository extends JpaRepository<SignalsHistory, Long> {
  List<SignalsHistory> findByLoaderCodeAndLoadTimeStampBetween(String loaderCode, Instant from, Instant to);
  List<SignalsHistory> findBySegmentCodeAndLoadTimeStampBetween(String segmentCode, Instant from, Instant to);
  List<SignalsHistory> findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(String loaderCode, String segmentCode, Instant from, Instant to);

  /**
   * Deletes signals history records for a loader within time range.
   * Used by backfill jobs with PURGE_AND_RELOAD strategy.
   *
   * @param loaderCode Loader code
   * @param fromTime  Start of time range
   * @param toTime    End of time range
   * @return Number of records deleted
   */
  @Modifying
  @Query("DELETE FROM SignalsHistory s WHERE s.loaderCode = :loaderCode " +
         "AND s.loadTimeStamp >= :fromTime AND s.loadTimeStamp <= :toTime")
  long deleteByLoaderCodeAndLoadTimeStampBetween(
      @Param("loaderCode") String loaderCode,
      @Param("fromTime") Instant fromTime,
      @Param("toTime") Instant toTime
  );

  /**
   * Deletes orphaned signals from FAILED loads.
   *
   * <p>Uses load_history_id FK to identify signals inserted by FAILED load_history records.
   * This is the CORRECT way to detect orphans (not time-based matching).
   *
   * <p><b>SQL:</b> DELETE FROM signals_history WHERE load_history_id IN (SELECT id FROM load_history WHERE status='FAILED')
   *
   * @return Number of orphaned records deleted
   */
  @Transactional
  @Modifying
  @Query(value = "DELETE FROM signals.signals_history " +
                 "WHERE load_history_id IN (" +
                 "  SELECT id FROM loader.load_history WHERE status = 'FAILED'" +
                 ")",
         nativeQuery = true)
  long deleteByLoadHistoryIdInFailedLoads();
}
