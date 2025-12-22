// src/main/java/com/tiqmo/monitoring/loader/domain/signals/entity/SignalsHistory.java
package com.tiqmo.monitoring.loader.domain.signals.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "signals_history", schema = "signals")
public class SignalsHistory {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name="loader_code", nullable=false, length=64)
  private String loaderCode;

  @Column(name="load_time_stamp", nullable=false)
  private Instant loadTimeStamp;

  @Column(name="segment_code", length=128)
  private String segmentCode;

  @Column(name="rec_count")  private Long recCount;
  @Column(name="max_val")    private Double maxVal;
  @Column(name="min_val")    private Double minVal;
  @Column(name="avg_val")    private Double avgVal;
  @Column(name="sum_val")    private Double sumVal;

  @Column(name="created_at", insertable=false, updatable=false)
  private Instant createdAt;

  /**
   * Reference to the load_history record that inserted this signal.
   * Used for orphaned data cleanup: DELETE WHERE load_history_id IN (SELECT id FROM load_history WHERE status='FAILED')
   * NULL for backfill jobs (not tracked in load_history).
   */
  @Column(name="load_history_id")
  private Long loadHistoryId;
}
