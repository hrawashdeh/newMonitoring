package com.tiqmo.monitoring.loader.dto.admin;

import com.tiqmo.monitoring.loader.domain.loader.entity.BackfillJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for backfill job details.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackfillJobResponse {

    private Long id;
    private String loaderCode;
    private Long fromTimeEpoch;
    private Long toTimeEpoch;
    private String purgeStrategy;
    private String status;
    private Long startTimeEpoch;
    private Long endTimeEpoch;
    private Long durationSeconds;
    private Long recordsPurged;
    private Long recordsLoaded;
    private Long recordsIngested;
    private String errorMessage;
    private String requestedBy;
    private Long requestedAtEpoch;
    private String replicaName;

    /**
     * Converts BackfillJob entity to DTO.
     */
    public static BackfillJobResponse fromEntity(BackfillJob job) {
        return BackfillJobResponse.builder()
            .id(job.getId())
            .loaderCode(job.getLoaderCode())
            .fromTimeEpoch(job.getFromTimeEpoch())
            .toTimeEpoch(job.getToTimeEpoch())
            .purgeStrategy(job.getPurgeStrategy() != null ? job.getPurgeStrategy().name() : null)
            .status(job.getStatus() != null ? job.getStatus().name() : null)
            .startTimeEpoch(job.getStartTime() != null ? job.getStartTime().getEpochSecond() : null)
            .endTimeEpoch(job.getEndTime() != null ? job.getEndTime().getEpochSecond() : null)
            .durationSeconds(job.getDurationSeconds())
            .recordsPurged(job.getRecordsPurged())
            .recordsLoaded(job.getRecordsLoaded())
            .recordsIngested(job.getRecordsIngested())
            .errorMessage(job.getErrorMessage())
            .requestedBy(job.getRequestedBy())
            .requestedAtEpoch(job.getRequestedAt() != null ? job.getRequestedAt().getEpochSecond() : null)
            .replicaName(job.getReplicaName())
            .build();
    }
}
