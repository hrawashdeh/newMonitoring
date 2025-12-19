// src/main/java/com/tiqmo/monitoring/loader/domain/signals/repo/SignalsHistoryRepository.java
package com.tiqmo.monitoring.loader.domain.signals.repo;

import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SignalsHistoryRepository extends JpaRepository<SignalsHistory, Long> {
  List<SignalsHistory> findByLoaderCodeAndLoadTimeStampBetween(String loaderCode, long from, long to);
  List<SignalsHistory> findBySegmentCodeAndLoadTimeStampBetween(String segmentCode, long from, long to);
  List<SignalsHistory> findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(String loaderCode, String segmentCode, long from, long to);

  /**
   * Deletes signals history records for a loader within time range.
   * Used by backfill jobs with PURGE_AND_RELOAD strategy.
   *
   * @param loaderCode Loader code
   * @param fromEpoch  Start of time range (Unix epoch seconds)
   * @param toEpoch    End of time range (Unix epoch seconds)
   * @return Number of records deleted
   */
  @Modifying
  @Query("DELETE FROM SignalsHistory s WHERE s.loaderCode = :loaderCode " +
         "AND s.loadTimeStamp >= :fromEpoch AND s.loadTimeStamp <= :toEpoch")
  long deleteByLoaderCodeAndLoadTimeStampBetween(
      @Param("loaderCode") String loaderCode,
      @Param("fromEpoch") long fromEpoch,
      @Param("toEpoch") long toEpoch
  );
}
