package com.tiqmo.monitoring.loader.dto.loader;

import com.tiqmo.monitoring.loader.domain.signals.entity.SegmentCombination;
import jakarta.persistence.Column;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkSegmentCombination {
    private String loaderCode;
    private List<CombinationData> segments;






    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CombinationData {
        private String segmentCode;
        private String segment1;
        private String segment2;
        private String segment3;
        private String segment4;
        private String segment5;
        private String segment6;
        private String segment7;
        private String segment8;
        private String segment9;
        private String segment10;
    }

}