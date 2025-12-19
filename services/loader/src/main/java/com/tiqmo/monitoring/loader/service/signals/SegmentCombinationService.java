// src/main/java/com/tiqmo/monitoring/loader/service/signals/SegmentCombinationService.java
package com.tiqmo.monitoring.loader.service.signals;

import com.tiqmo.monitoring.loader.domain.signals.entity.SegmentCombination;
import com.tiqmo.monitoring.loader.domain.signals.entity.SegmentCombinationId;
import com.tiqmo.monitoring.loader.domain.signals.repo.SegmentCombinationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing segment combinations with auto-incrementing segment codes.
 *
 * <p><b>Segment Lookup/Creation Flow:</b>
 * <ol>
 *   <li>Extract 10 segment values from query result</li>
 *   <li>Look up segment_combination by (loader_code + seg1-seg10)</li>
 *   <li>If found: return existing segment_code</li>
 *   <li>If not found:
 *     <ul>
 *       <li>Get max segment_code for this loader</li>
 *       <li>Create new entry with segment_code = max + 1 (or 1 if first)</li>
 *       <li>Save to database</li>
 *       <li>Return new segment_code</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><b>Thread Safety:</b>
 * Uses @Transactional with PESSIMISTIC_WRITE lock to ensure thread-safe segment_code generation
 * across multiple replicas/pods.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SegmentCombinationService {

  private final SegmentCombinationRepository repository;

  /**
   * Gets or creates a segment combination for the given loader and segment values.
   *
   * <p>If the combination exists, returns the existing segment_code.
   * If not, creates a new entry with auto-incremented segment_code.
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
   * @return the segment_code (existing or newly created)
   */
  @Transactional
  public Long getOrCreateSegmentCode(
      String loaderCode,
      String seg1, String seg2, String seg3, String seg4, String seg5,
      String seg6, String seg7, String seg8, String seg9, String seg10
  ) {
    // 1. Try to find existing combination
    var existing = repository.findByLoaderCodeAndSegments(
        loaderCode, seg1, seg2, seg3, seg4, seg5, seg6, seg7, seg8, seg9, seg10
    );

    if (existing.isPresent()) {
      Long segmentCode = existing.get().getId().getSegmentCode();
      log.debug("Found existing segment_code {} for loader {} with segments [{}, {}, {}, {}, {}, {}, {}, {}, {}, {}]",
          segmentCode, loaderCode, seg1, seg2, seg3, seg4, seg5, seg6, seg7, seg8, seg9, seg10);
      return segmentCode;
    }

    // 2. No existing combination - create new one with auto-incremented segment_code
    Long maxSegmentCode = repository.findMaxSegmentCodeByLoaderCode(loaderCode).orElse(0L);
    Long newSegmentCode = maxSegmentCode + 1;

    log.info("Creating new segment_code {} for loader {} with segments [{}, {}, {}, {}, {}, {}, {}, {}, {}, {}]",
        newSegmentCode, loaderCode, seg1, seg2, seg3, seg4, seg5, seg6, seg7, seg8, seg9, seg10);

    // 3. Build and save new combination
    SegmentCombinationId id = new SegmentCombinationId(loaderCode, newSegmentCode);
    SegmentCombination newCombination = SegmentCombination.builder()
        .id(id)
        .segment1(seg1)
        .segment2(seg2)
        .segment3(seg3)
        .segment4(seg4)
        .segment5(seg5)
        .segment6(seg6)
        .segment7(seg7)
        .segment8(seg8)
        .segment9(seg9)
        .segment10(seg10)
        .build();

    repository.save(newCombination);

    return newSegmentCode;
  }

  /**
   * DTO for passing 10 segment values as a single object.
   */
  public record SegmentValues(
      String seg1, String seg2, String seg3, String seg4, String seg5,
      String seg6, String seg7, String seg8, String seg9, String seg10
  ) {
    /**
     * Creates a SegmentValues with all nulls.
     */
    public static SegmentValues empty() {
      return new SegmentValues(null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Checks if all segment values are null.
     */
    public boolean isEmpty() {
      return seg1 == null && seg2 == null && seg3 == null && seg4 == null && seg5 == null &&
             seg6 == null && seg7 == null && seg8 == null && seg9 == null && seg10 == null;
    }
  }
}
