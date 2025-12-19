// src/main/java/com/tiqmo/monitoring/loader/domain/signals/repo/SegmentCombinationRepository.java
package com.tiqmo.monitoring.loader.domain.signals.repo;

import com.tiqmo.monitoring.loader.domain.signals.entity.SegmentCombination;
import com.tiqmo.monitoring.loader.domain.signals.entity.SegmentCombinationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SegmentCombination entity.
 *
 * <p>Supports segment lookup by 10 segment dimensions and auto-incrementing segment_code per loader.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public interface SegmentCombinationRepository extends JpaRepository<SegmentCombination, SegmentCombinationId> {

  /**
   * Finds a segment combination by loader code and all 10 segment values.
   * Handles NULL values in segment fields (uses IS NULL comparison).
   *
   * @param loaderCode the loader code
   * @param seg1 segment 1 value (nullable)
   * @param seg2 segment 2 value (nullable)
   * @param seg3 segment 3 value (nullable)
   * @param seg4 segment 4 value (nullable)
   * @param seg5 segment 5 value (nullable)
   * @param seg6 segment 6 value (nullable)
   * @param seg7 segment 7 value (nullable)
   * @param seg8 segment 8 value (nullable)
   * @param seg9 segment 9 value (nullable)
   * @param seg10 segment 10 value (nullable)
   * @return the matching segment combination, or empty if not found
   */
  @Query("""
      SELECT sc FROM SegmentCombination sc
      WHERE sc.id.loaderCode = :loaderCode
        AND (sc.segment1 = :seg1 OR (sc.segment1 IS NULL AND :seg1 IS NULL))
        AND (sc.segment2 = :seg2 OR (sc.segment2 IS NULL AND :seg2 IS NULL))
        AND (sc.segment3 = :seg3 OR (sc.segment3 IS NULL AND :seg3 IS NULL))
        AND (sc.segment4 = :seg4 OR (sc.segment4 IS NULL AND :seg4 IS NULL))
        AND (sc.segment5 = :seg5 OR (sc.segment5 IS NULL AND :seg5 IS NULL))
        AND (sc.segment6 = :seg6 OR (sc.segment6 IS NULL AND :seg6 IS NULL))
        AND (sc.segment7 = :seg7 OR (sc.segment7 IS NULL AND :seg7 IS NULL))
        AND (sc.segment8 = :seg8 OR (sc.segment8 IS NULL AND :seg8 IS NULL))
        AND (sc.segment9 = :seg9 OR (sc.segment9 IS NULL AND :seg9 IS NULL))
        AND (sc.segment10 = :seg10 OR (sc.segment10 IS NULL AND :seg10 IS NULL))
      """)
  Optional<SegmentCombination> findByLoaderCodeAndSegments(
      @Param("loaderCode") String loaderCode,
      @Param("seg1") String seg1,
      @Param("seg2") String seg2,
      @Param("seg3") String seg3,
      @Param("seg4") String seg4,
      @Param("seg5") String seg5,
      @Param("seg6") String seg6,
      @Param("seg7") String seg7,
      @Param("seg8") String seg8,
      @Param("seg9") String seg9,
      @Param("seg10") String seg10
  );

  /**
   * Gets the maximum segment_code for a given loader.
   * Used to generate the next segment_code when creating new combinations.
   *
   * @param loaderCode the loader code
   * @return the maximum segment_code, or empty if no segments exist for this loader
   */
  @Query("SELECT MAX(sc.id.segmentCode) FROM SegmentCombination sc WHERE sc.id.loaderCode = :loaderCode")
  Optional<Long> findMaxSegmentCodeByLoaderCode(@Param("loaderCode") String loaderCode);

  /**
   * Finds all segment combinations for a given loader.
   *
   * @param loaderCode the loader code
   * @return list of all segment combinations for this loader
   */
  @Query("SELECT sc FROM SegmentCombination sc WHERE sc.id.loaderCode = :loaderCode")
  List<SegmentCombination> findAllByLoaderCode(@Param("loaderCode") String loaderCode);
}
