// src/main/java/com/tiqmo/monitoring/loader/service/signals/SignalsIngestService.java
package com.tiqmo.monitoring.loader.service.signals;

import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import com.tiqmo.monitoring.loader.domain.signals.repo.SignalsHistoryRepository;
import com.tiqmo.monitoring.loader.dto.common.ErrorCode;
import com.tiqmo.monitoring.loader.dto.signals.BulkSignalsRequest;
import com.tiqmo.monitoring.loader.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for ingesting signals history data.
 *
 * <p>Provides append-only operations for signal data with comprehensive
 * logging, validation, and error handling.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignalsIngestService {
    private final SignalsHistoryRepository repo;

    /**
     * Appends a single signal to history.
     *
     * @param signal Signal to append
     * @return Saved signal
     * @throws BusinessException if validation fails
     */
    @Transactional
    public SignalsHistory append(SignalsHistory signal) {
        MDC.put("loaderCode", signal.getLoaderCode());

        try {
            log.info("Appending signal | loaderCode={} | timestamp={}",
                signal.getLoaderCode(), signal.getLoadTimeStamp());

            // Validation
            validateSignal(signal);

            // Set createTime if not set
            if (signal.getCreateTime() == null) {
                signal.setCreateTime(System.currentTimeMillis() / 1000);
                log.debug("Set createTime to current timestamp");
            }

            SignalsHistory saved = repo.save(signal);
            log.info("Signal saved | id={}", saved.getId());

            return saved;

        } finally {
            MDC.remove("loaderCode");
        }
    }

    /**
     * Appends multiple signals in bulk.
     *
     * @param loaderCode Loader code for all signals
     * @param signalDataList List of signal data
     * @return List of saved signals
     * @throws BusinessException if validation fails
     */
    @Transactional
    public List<SignalsHistory> bulkAppend(String loaderCode, List<BulkSignalsRequest.SignalData> signalDataList) {
        MDC.put("loaderCode", loaderCode);

        try {
            log.info("Bulk appending signals | loaderCode={} | count={}",
                loaderCode, signalDataList.size());

            // Validation
            if (loaderCode == null || loaderCode.isBlank()) {
                log.warn("Loader code is null or blank");
                throw new BusinessException(
                    ErrorCode.VALIDATION_REQUIRED_FIELD,
                    "Loader code is required",
                    "loaderCode"
                );
            }

            if (signalDataList == null || signalDataList.isEmpty()) {
                log.warn("Signal data list is null or empty");
                throw new BusinessException(
                    ErrorCode.VALIDATION_REQUIRED_FIELD,
                    "Signal data list is required and cannot be empty",
                    "signalDataList"
                );
            }

            // Convert DTOs to entities
            List<SignalsHistory> signals = signalDataList.stream()
                    .map(data -> data.toEntity(loaderCode))
                    .toList();

            log.debug("Converted {} DTOs to entities", signals.size());

            // Set createTime for all records
            long now = System.currentTimeMillis() / 1000;
            signals.forEach(s -> {
                if (s.getCreateTime() == null) {
                    s.setCreateTime(now);
                }
            });

            log.debug("Set createTime for all signals");

            List<SignalsHistory> saved = repo.saveAll(signals);
            log.info("Bulk append completed | savedCount={}", saved.size());

            return saved;

        } finally {
            MDC.remove("loaderCode");
        }
    }

    /**
     * Validates a signal before saving.
     *
     * @param signal Signal to validate
     * @throws BusinessException if validation fails
     */
    private void validateSignal(SignalsHistory signal) {
        if (signal.getLoaderCode() == null || signal.getLoaderCode().isBlank()) {
            log.warn("Loader code is null or blank");
            throw new BusinessException(
                ErrorCode.VALIDATION_REQUIRED_FIELD,
                "Loader code is required",
                "loaderCode"
            );
        }

        if (signal.getLoadTimeStamp() == null) {
            log.warn("Load timestamp is null | loaderCode={}", signal.getLoaderCode());
            throw new BusinessException(
                ErrorCode.SIGNAL_INVALID_TIMESTAMP,
                "Load timestamp is required"
            );
        }

        log.debug("Signal validation passed | loaderCode={}", signal.getLoaderCode());
    }
}
