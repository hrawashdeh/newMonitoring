package com.tiqmo.monitoring.loader.dto.signals;

import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Bulk signals ingestion request with validation constraints.
 *
 * @author Hassan Rawashdeh
 * @since 2025-11-20
 */
@Data
public class BulkSignalsRequest {
    @NotBlank(message = "Loader code is required")
    @Size(min = 1, max = 64, message = "Loader code must be between 1 and 64 characters")
    private String loaderCode;

    @NotNull(message = "Signals list is required")
    @NotEmpty(message = "Signals list cannot be empty")
    @Size(max = 10000, message = "Cannot ingest more than 10000 signals in a single request")
    @Valid
    private List<SignalData> signals;

    /**
     * Signal data with validation constraints.
     */
    @Data
    public static class SignalData {
        @NotNull(message = "Load timestamp is required")
        @Min(value = 1000000000, message = "Load timestamp must be a valid epoch second (> 1000000000)")
        private Long loadTimeStamp;

        @Size(max = 64, message = "Segment code must not exceed 64 characters")
        private String segmentCode;

        @Min(value = 0, message = "Record count cannot be negative")
        private Long recCount;

        private Double maxVal;
        private Double minVal;
        private Double avgVal;
        private Double sumVal;

        public SignalsHistory toEntity(String loaderCode) {
            SignalsHistory s = new SignalsHistory();
            s.setLoaderCode(loaderCode);
            // Convert epoch seconds to Instant
            s.setLoadTimeStamp(loadTimeStamp != null ? Instant.ofEpochSecond(loadTimeStamp) : null);
            s.setSegmentCode(segmentCode);
            s.setRecCount(recCount);
            s.setMaxVal(maxVal);
            s.setMinVal(minVal);
            s.setAvgVal(avgVal);
            s.setSumVal(sumVal);
            return s;
        }
    }
}
