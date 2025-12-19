// src/main/java/com/tiqmo/monitoring/loader/domain/signals/entity/SegmentDictionary.java
package com.tiqmo.monitoring.loader.domain.loader.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "segments_dictionary", schema = "loader",
       indexes = @Index(name="ix_seg_loader_segnum", columnList="loader,segment_number"))
public class SegmentDictionary {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name="loader", nullable=false, length=64)
  private String loader;

  @Column(name="segment_number", nullable=false)
  private Integer segmentNumber;

  @Column(name="segment_description", length=256)
  private String segmentDescription;
}
