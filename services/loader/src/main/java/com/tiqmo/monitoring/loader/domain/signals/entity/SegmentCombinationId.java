// src/main/java/com/tiqmo/monitoring/loader/domain/signals/entity/SegmentCombinationId.java
package com.tiqmo.monitoring.loader.domain.signals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for SegmentCombination.
 * Combines loader_code and segment_code.
 *
 * <p>This allows each loader to have its own independent segment_code sequence,
 * supporting auto-incrementing segment codes per loader.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SegmentCombinationId implements Serializable {

  @Column(name = "loader_code", nullable = false, length = 64)
  private String loaderCode;

  @Column(name = "segment_code", nullable = false)
  private Long segmentCode;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SegmentCombinationId that)) return false;
    return Objects.equals(loaderCode, that.loaderCode) &&
           Objects.equals(segmentCode, that.segmentCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(loaderCode, segmentCode);
  }

  @Override
  public String toString() {
    return loaderCode + ":" + segmentCode;
  }
}
