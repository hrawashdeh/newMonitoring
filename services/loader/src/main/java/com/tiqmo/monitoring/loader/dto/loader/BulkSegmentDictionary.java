package com.tiqmo.monitoring.loader.dto.loader;

import com.tiqmo.monitoring.loader.domain.loader.entity.SegmentDictionary;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor

@Builder
public class BulkSegmentDictionary {
    private String loaderCode;
    private List<SegmentData> segments;



    public BulkSegmentDictionary(String loaderCode, List<SegmentData> segment) {
        this.loaderCode = loaderCode;
        this.segments = segment.stream().map(s -> SegmentData.builder()
                .segmentNumber(s.getSegmentNumber())
                .segmentDescription(s.getSegmentDescription())
                .build()).toList();
    }


    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SegmentData {
        private Integer segmentNumber;
        private String segmentDescription;
    }

}