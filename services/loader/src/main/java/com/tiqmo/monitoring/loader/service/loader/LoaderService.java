package com.tiqmo.monitoring.loader.service.loader;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.dto.common.ErrorCode;
import com.tiqmo.monitoring.loader.dto.loader.EtlLoaderDto;
import com.tiqmo.monitoring.loader.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing ETL loaders.
 *
 * <p>Provides CRUD operations for loader configurations with comprehensive
 * logging, validation, and error handling.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class LoaderService {

    private final LoaderRepository repo;

    /**
     * Finds all loaders.
     *
     * @return List of all loaders
     */
    public List<EtlLoaderDto> findAll() {
        log.debug("Fetching all loaders");

        List<EtlLoaderDto> loaders = repo.findAll().stream()
            .map(this::toDto)
            .toList();

        log.info("Found {} loaders", loaders.size());
        return loaders;
    }

    /**
     * Finds loader by code.
     *
     * @param loaderCode Loader code
     * @return Loader DTO
     * @throws BusinessException if loader not found
     */
    public EtlLoaderDto getByCode(String loaderCode) {
        MDC.put("loaderCode", loaderCode);

        try {
            log.debug("Fetching loader by code: {}", loaderCode);

            // Validation
            if (loaderCode == null || loaderCode.isBlank()) {
                log.warn("Loader code is null or blank");
                throw new BusinessException(
                    ErrorCode.VALIDATION_REQUIRED_FIELD,
                    "Loader code is required",
                    "loaderCode"
                );
            }

            EtlLoaderDto loader = repo.findByLoaderCode(loaderCode)
                .map(this::toDto)
                .orElseThrow(() -> {
                    log.warn("Loader not found: {}", loaderCode);
                    return new BusinessException(
                        ErrorCode.LOADER_NOT_FOUND,
                        "Loader with code '" + loaderCode + "' not found"
                    );
                });

            log.debug("Loader found: {}", loaderCode);
            return loader;

        } finally {
            MDC.remove("loaderCode");
        }
    }

    /**
     * Creates a new loader.
     *
     * @param dto Loader DTO
     * @return Created loader DTO
     * @throws BusinessException if validation fails or loader already exists
     */
    @Transactional
    public EtlLoaderDto create(EtlLoaderDto dto) {
        MDC.put("loaderCode", dto.getLoaderCode());

        try {
            log.info("Creating new loader: {}", dto.getLoaderCode());

            // Validation
            validateLoaderDto(dto);

            // Check for duplicate
            if (repo.existsByLoaderCode(dto.getLoaderCode())) {
                log.warn("Loader already exists: {}", dto.getLoaderCode());
                throw new BusinessException(
                    ErrorCode.LOADER_ALREADY_EXISTS,
                    "Loader with code '" + dto.getLoaderCode() + "' already exists",
                    "loaderCode"
                );
            }

            Loader saved = repo.save(toEntity(dto));
            log.info("Loader created successfully: {} | id={}", saved.getLoaderCode(), saved.getId());

            return toDto(saved);

        } finally {
            MDC.remove("loaderCode");
        }
    }

    /**
     * Updates or creates a loader (upsert operation).
     *
     * @param dto Loader DTO
     * @return Updated/created loader DTO
     * @throws BusinessException if validation fails
     */
    @Transactional
    public EtlLoaderDto upsert(EtlLoaderDto dto) {
        MDC.put("loaderCode", dto.getLoaderCode());

        try {
            log.info("Upserting loader: {}", dto.getLoaderCode());

            // Validation
            validateLoaderDto(dto);

            Loader entity = repo.findByLoaderCode(dto.getLoaderCode())
                .orElseGet(() -> {
                    log.debug("Loader not found, creating new: {}", dto.getLoaderCode());
                    return new Loader();
                });

            boolean isNew = entity.getId() == null;

            entity.setLoaderCode(dto.getLoaderCode());
            entity.setLoaderSql(dto.getLoaderSql());
            entity.setMinIntervalSeconds(dto.getMinIntervalSeconds());
            entity.setMaxIntervalSeconds(dto.getMaxIntervalSeconds());
            entity.setMaxQueryPeriodSeconds(dto.getMaxQueryPeriodSeconds());
            entity.setMaxParallelExecutions(dto.getMaxParallelExecutions());
            entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);

            Loader saved = repo.save(entity);

            if (isNew) {
                log.info("Loader created via upsert: {} | id={}", saved.getLoaderCode(), saved.getId());
            } else {
                log.info("Loader updated via upsert: {} | id={}", saved.getLoaderCode(), saved.getId());
            }

            return toDto(saved);

        } finally {
            MDC.remove("loaderCode");
        }
    }

    /**
     * Deletes a loader by code.
     *
     * @param loaderCode Loader code
     * @throws BusinessException if loader not found
     */
    @Transactional
    public void deleteByCode(String loaderCode) {
        MDC.put("loaderCode", loaderCode);

        try {
            log.info("Deleting loader: {}", loaderCode);

            // Validation
            if (loaderCode == null || loaderCode.isBlank()) {
                log.warn("Loader code is null or blank");
                throw new BusinessException(
                    ErrorCode.VALIDATION_REQUIRED_FIELD,
                    "Loader code is required",
                    "loaderCode"
                );
            }

            Loader loader = repo.findByLoaderCode(loaderCode)
                .orElseThrow(() -> {
                    log.warn("Cannot delete: Loader not found: {}", loaderCode);
                    return new BusinessException(
                        ErrorCode.LOADER_NOT_FOUND,
                        "Cannot delete: Loader with code '" + loaderCode + "' not found"
                    );
                });

            repo.delete(loader);
            log.info("Loader deleted successfully: {}", loaderCode);

        } finally {
            MDC.remove("loaderCode");
        }
    }

    /**
     * Validates loader DTO.
     *
     * @param dto Loader DTO
     * @throws BusinessException if validation fails
     */
    private void validateLoaderDto(EtlLoaderDto dto) {
        if (dto.getLoaderCode() == null || dto.getLoaderCode().isBlank()) {
            throw new BusinessException(
                ErrorCode.VALIDATION_REQUIRED_FIELD,
                "Loader code is required",
                "loaderCode"
            );
        }

        if (dto.getLoaderSql() == null || dto.getLoaderSql().isBlank()) {
            throw new BusinessException(
                ErrorCode.VALIDATION_REQUIRED_FIELD,
                "Loader SQL is required",
                "loaderSql"
            );
        }

        if (dto.getMinIntervalSeconds() != null && dto.getMinIntervalSeconds() <= 0) {
            throw new BusinessException(
                ErrorCode.VALIDATION_INVALID_VALUE,
                "Min interval must be greater than 0",
                "minIntervalSeconds"
            );
        }

        if (dto.getMaxIntervalSeconds() != null && dto.getMaxIntervalSeconds() <= 0) {
            throw new BusinessException(
                ErrorCode.VALIDATION_INVALID_VALUE,
                "Max interval must be greater than 0",
                "maxIntervalSeconds"
            );
        }

        if (dto.getMinIntervalSeconds() != null && dto.getMaxIntervalSeconds() != null &&
            dto.getMinIntervalSeconds() > dto.getMaxIntervalSeconds()) {
            throw new BusinessException(
                ErrorCode.VALIDATION_INVALID_VALUE,
                "Min interval cannot be greater than max interval",
                "minIntervalSeconds"
            );
        }

        if (dto.getMaxParallelExecutions() != null && dto.getMaxParallelExecutions() <= 0) {
            throw new BusinessException(
                ErrorCode.VALIDATION_INVALID_VALUE,
                "Max parallel executions must be greater than 0",
                "maxParallelExecutions"
            );
        }

        log.debug("Loader DTO validation passed: {}", dto.getLoaderCode());
    }

    private EtlLoaderDto toDto(Loader e) {
        return EtlLoaderDto.builder()
                .id(e.getId())
                .loaderCode(e.getLoaderCode())
                .loaderSql(e.getLoaderSql())
                .minIntervalSeconds(e.getMinIntervalSeconds())
                .maxIntervalSeconds(e.getMaxIntervalSeconds())
                .maxQueryPeriodSeconds(e.getMaxQueryPeriodSeconds())
                .maxParallelExecutions(e.getMaxParallelExecutions())
                .enabled(e.isEnabled())
                .build();
    }

    private Loader toEntity(EtlLoaderDto d) {
        return Loader.builder()
                .id(d.getId())
                .loaderCode(d.getLoaderCode())
                .loaderSql(d.getLoaderSql())
                .minIntervalSeconds(d.getMinIntervalSeconds())
                .maxIntervalSeconds(d.getMaxIntervalSeconds())
                .maxQueryPeriodSeconds(d.getMaxQueryPeriodSeconds())
                .maxParallelExecutions(d.getMaxParallelExecutions())
                .enabled(d.getEnabled() != null ? d.getEnabled() : true)
                .build();
    }


}
