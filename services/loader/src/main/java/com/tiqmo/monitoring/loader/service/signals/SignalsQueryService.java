// src/main/java/com/tiqmo/monitoring/loader/service/signals/SignalsQueryService.java
package com.tiqmo.monitoring.loader.service.signals;

import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import com.tiqmo.monitoring.loader.domain.signals.repo.SignalsHistoryRepository;
import com.tiqmo.monitoring.loader.dto.common.ErrorCode;
import com.tiqmo.monitoring.loader.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for querying signals history data.
 *
 * <p>Provides read-only query operations for signal data with time range
 * filtering and comprehensive logging.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SignalsQueryService {
    private final SignalsHistoryRepository repo;

    /**
     * Queries signals by loader code within a time range.
     *
     * @param loaderCode Loader code
     * @param fromEpoch Start time (epoch seconds)
     * @param toEpoch End time (epoch seconds)
     * @return List of signals in the time range
     * @throws BusinessException if validation fails
     */
    public List<SignalsHistory> byLoaderBetween(String loaderCode, long fromEpoch, long toEpoch) {
        MDC.put("loaderCode", loaderCode);

        try {
            log.trace("Entering byLoaderBetween() | loaderCode={} | fromEpoch={} | toEpoch={} | correlationId={} | processId={}",
                    loaderCode, fromEpoch, toEpoch, MDC.get("correlationId"), MDC.get("processId"));
            log.info("Querying signals by loader | loaderCode={} | timeRange=[{}, {}]",
                loaderCode, fromEpoch, toEpoch);

            // Validation
            log.debug("Validating query parameters | loaderCode={}", loaderCode);
            validateLoaderCode(loaderCode);
            validateTimeRange(fromEpoch, toEpoch);

            // Convert epoch to Instant
            log.trace("Converting epoch times to Instant | fromEpoch={} | toEpoch={}", fromEpoch, toEpoch);
            Instant fromTime = Instant.ofEpochSecond(fromEpoch);
            Instant toTime = Instant.ofEpochSecond(toEpoch);

            log.debug("Executing repository query | loaderCode={} | fromTime={} | toTime={}",
                    loaderCode, fromTime, toTime);
            List<SignalsHistory> results = repo.findByLoaderCodeAndLoadTimeStampBetween(
                loaderCode, fromTime, toTime);

            log.info("Query completed | loaderCode={} | resultCount={} | correlationId={}",
                    loaderCode, results.size(), MDC.get("correlationId"));
            log.trace("Exiting byLoaderBetween() | resultCount={} | success=true", results.size());

            return results;

        } finally {
            MDC.remove("loaderCode");
        }
    }

    /**
     * Queries signals by loader code and segment code within a time range.
     *
     * @param loaderCode Loader code
     * @param segmentCode Segment code
     * @param fromEpoch Start time (epoch seconds)
     * @param toEpoch End time (epoch seconds)
     * @return List of signals matching the criteria
     * @throws BusinessException if validation fails
     */
    public List<SignalsHistory> byLoaderAndSegmentBetween(String loaderCode, String segmentCode,
                                                           long fromEpoch, long toEpoch) {
        MDC.put("loaderCode", loaderCode);
        MDC.put("segmentCode", segmentCode);

        try {
            log.trace("Entering byLoaderAndSegmentBetween() | loaderCode={} | segmentCode={} | fromEpoch={} | toEpoch={} | correlationId={} | processId={}",
                    loaderCode, segmentCode, fromEpoch, toEpoch, MDC.get("correlationId"), MDC.get("processId"));
            log.info("Querying signals by loader and segment | loaderCode={} | segmentCode={} | timeRange=[{}, {}]",
                loaderCode, segmentCode, fromEpoch, toEpoch);

            // Validation
            log.debug("Validating query parameters | loaderCode={} | segmentCode={}", loaderCode, segmentCode);
            validateLoaderCode(loaderCode);
            validateSegmentCode(segmentCode);
            validateTimeRange(fromEpoch, toEpoch);

            // Convert epoch to Instant
            log.trace("Converting epoch times to Instant | fromEpoch={} | toEpoch={}", fromEpoch, toEpoch);
            Instant fromTime = Instant.ofEpochSecond(fromEpoch);
            Instant toTime = Instant.ofEpochSecond(toEpoch);

            log.debug("Executing repository query | loaderCode={} | segmentCode={} | fromTime={} | toTime={}",
                    loaderCode, segmentCode, fromTime, toTime);
            List<SignalsHistory> results = repo.findByLoaderCodeAndSegmentCodeAndLoadTimeStampBetween(
                loaderCode, segmentCode, fromTime, toTime);

            log.info("Query completed | loaderCode={} | segmentCode={} | resultCount={} | correlationId={}",
                loaderCode, segmentCode, results.size(), MDC.get("correlationId"));
            log.trace("Exiting byLoaderAndSegmentBetween() | resultCount={} | success=true", results.size());

            return results;

        } finally {
            MDC.remove("loaderCode");
            MDC.remove("segmentCode");
        }
    }

    /**
     * Validates loader code.
     *
     * @param loaderCode Loader code to validate
     * @throws BusinessException if loader code is null or blank
     */
    private void validateLoaderCode(String loaderCode) {
        if (loaderCode == null || loaderCode.isBlank()) {
            log.warn("Validation failed: Loader code is null or blank | correlationId={}", MDC.get("correlationId"));
            throw new BusinessException(
                ErrorCode.VALIDATION_REQUIRED_FIELD,
                "Loader code is required",
                "loaderCode"
            );
        }
        log.trace("Loader code validation passed | loaderCode={}", loaderCode);
    }

    /**
     * Validates segment code.
     *
     * @param segmentCode Segment code to validate
     * @throws BusinessException if segment code is null or blank
     */
    private void validateSegmentCode(String segmentCode) {
        if (segmentCode == null || segmentCode.isBlank()) {
            log.warn("Segment code is null or blank");
            throw new BusinessException(
                ErrorCode.VALIDATION_REQUIRED_FIELD,
                "Segment code is required",
                "segmentCode"
            );
        }
    }

    /**
     * Validates time range.
     *
     * @param fromEpoch Start time
     * @param toEpoch End time
     * @throws BusinessException if time range is invalid
     */
    private void validateTimeRange(long fromEpoch, long toEpoch) {
        if (fromEpoch < 0) {
            log.warn("Invalid from time | fromEpoch={}", fromEpoch);
            throw new BusinessException(
                ErrorCode.VALIDATION_INVALID_VALUE,
                "From time cannot be negative",
                "fromEpoch"
            );
        }

        if (toEpoch < 0) {
            log.warn("Invalid to time | toEpoch={}", toEpoch);
            throw new BusinessException(
                ErrorCode.VALIDATION_INVALID_VALUE,
                "To time cannot be negative",
                "toEpoch"
            );
        }

        if (fromEpoch >= toEpoch) {
            log.warn("Invalid time range | fromEpoch={} | toEpoch={}", fromEpoch, toEpoch);
            throw new BusinessException(
                ErrorCode.VALIDATION_INVALID_VALUE,
                "From time must be before to time"
            );
        }

        log.debug("Time range validation passed | fromEpoch={} | toEpoch={}", fromEpoch, toEpoch);
    }
}
