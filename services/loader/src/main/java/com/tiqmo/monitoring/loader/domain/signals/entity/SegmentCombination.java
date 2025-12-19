// src/main/java/com/tiqmo/monitoring/loader/domain/signals/entity/SegmentCombination.java
package com.tiqmo.monitoring.loader.domain.signals.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Segment combination table for mapping 10 segment dimensions to a unique segment code.
 *
 * <p><b>Composite Primary Key:</b>
 * <ul>
 *   <li>loader_code: Identifies the loader</li>
 *   <li>segment_code: Auto-incrementing per loader (starts at 1)</li>
 * </ul>
 *
 * <p><b>Segment Lookup/Creation Flow:</b>
 * <ol>
 *   <li>Query returns 10 segment values (seg1-seg10)</li>
 *   <li>Lookup segment_combination by (loader_code, seg1-seg10)</li>
 *   <li>If found: use existing segment_code</li>
 *   <li>If not found: create new entry with next segment_code</li>
 * </ol>
 *
 * <p><b>Example:</b>
 * <pre>
 * Loader: WALLET_TRX_STATS_01
 * Segment values: seg1=USA, seg2=CREDIT_CARD, seg3-seg10=null
 * → segment_code = 1 (auto-assigned on first occurrence)
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "segment_combination", schema = "signals",
       indexes = {
           @Index(name = "idx_segment_combination_lookup",
                  columnList = "loader_code, segment1, segment2, segment3, segment4, segment5, segment6, segment7, segment8, segment9, segment10")
       })
public class SegmentCombination {

  @EmbeddedId
  private SegmentCombinationId id;

  @Column(name = "segment1", length = 128)  private String segment1;
  @Column(name = "segment2", length = 128)  private String segment2;
  @Column(name = "segment3", length = 128)  private String segment3;
  @Column(name = "segment4", length = 128)  private String segment4;
  @Column(name = "segment5", length = 128)  private String segment5;
  @Column(name = "segment6", length = 128)  private String segment6;
  @Column(name = "segment7", length = 128)  private String segment7;
  @Column(name = "segment8", length = 128)  private String segment8;
  @Column(name = "segment9", length = 128)  private String segment9;
  @Column(name = "segment10", length = 128) private String segment10;
}
