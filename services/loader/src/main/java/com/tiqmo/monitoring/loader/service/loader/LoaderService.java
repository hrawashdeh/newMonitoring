package com.tiqmo.monitoring.loader.service.loader;

import com.tiqmo.monitoring.loader.domain.loader.entity.*;
import com.tiqmo.monitoring.loader.domain.loader.repo.ApprovalAuditLogRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.SourceDatabaseRepository;
import com.tiqmo.monitoring.loader.dto.common.ErrorCode;
import com.tiqmo.monitoring.loader.dto.loader.ActivityEventDto;
import com.tiqmo.monitoring.loader.dto.loader.EtlLoaderDto;
import com.tiqmo.monitoring.loader.dto.loader.LoadersStatsDto;
import com.tiqmo.monitoring.loader.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
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
    private final SourceDatabaseRepository sourceDbRepo;
    private final ApprovalAuditLogRepository auditLogRepo;

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

            // Fetch source database
            if (dto.getSourceDatabaseId() != null) {
                SourceDatabase sourceDb = sourceDbRepo.findById(dto.getSourceDatabaseId())
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.VALIDATION_INVALID_VALUE,
                                "Source database with ID " + dto.getSourceDatabaseId() + " not found",
                                "sourceDatabaseId"
                        ));
                entity.setSourceDatabase(sourceDb);
            }

            // Parse purge strategy
            if (dto.getPurgeStrategy() != null && !dto.getPurgeStrategy().isBlank()) {
                try {
                    entity.setPurgeStrategy(PurgeStrategy.valueOf(dto.getPurgeStrategy()));
                } catch (IllegalArgumentException e) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_INVALID_VALUE,
                            "Invalid purge strategy: " + dto.getPurgeStrategy() + ". Must be one of: FAIL_ON_DUPLICATE, PURGE_AND_RELOAD, SKIP_DUPLICATES",
                            "purgeStrategy"
                    );
                }
            }

            entity.setLoaderCode(dto.getLoaderCode());
            entity.setLoaderSql(dto.getLoaderSql());
            entity.setMinIntervalSeconds(dto.getMinIntervalSeconds());
            entity.setMaxIntervalSeconds(dto.getMaxIntervalSeconds());
            entity.setMaxQueryPeriodSeconds(dto.getMaxQueryPeriodSeconds());
            entity.setMaxParallelExecutions(dto.getMaxParallelExecutions());
            entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
            entity.setSourceTimezoneOffsetHours(dto.getSourceTimezoneOffsetHours() != null ? dto.getSourceTimezoneOffsetHours() : 0);
            entity.setAggregationPeriodSeconds(dto.getAggregationPeriodSeconds());

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
                // Source database ID and purge strategy
                .sourceDatabaseId(e.getSourceDatabase() != null ? e.getSourceDatabase().getId() : null)
                .purgeStrategy(e.getPurgeStrategy() != null ? e.getPurgeStrategy().name() : null)
                // Additional fields for frontend display
                .sourceTimezoneOffsetHours(e.getSourceTimezoneOffsetHours())
                .consecutiveZeroRecordRuns(e.getConsecutiveZeroRecordRuns())
                .aggregationPeriodSeconds(e.getAggregationPeriodSeconds())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .createdBy(null) // TODO: Audit user tracking not yet implemented
                .updatedBy(null) // TODO: Audit user tracking not yet implemented
                // Approval workflow fields
                .approvalStatus(e.getApprovalStatus() != null ? e.getApprovalStatus().name() : null)
                .approvedBy(e.getApprovedBy())
                .approvedAt(e.getApprovedAt())
                .rejectedBy(e.getRejectedBy())
                .rejectedAt(e.getRejectedAt())
                .rejectionReason(e.getRejectionReason())
                // Source database (password excluded for security)
                .sourceDatabase(toSourceDatabaseDto(e.getSourceDatabase()))
                .build();
    }

    private com.tiqmo.monitoring.loader.dto.loader.SourceDatabaseDto toSourceDatabaseDto(
            com.tiqmo.monitoring.loader.domain.loader.entity.SourceDatabase db) {
        if (db == null) {
            return null;
        }
        return com.tiqmo.monitoring.loader.dto.loader.SourceDatabaseDto.builder()
                .id(db.getId())
                .dbCode(db.getDbCode())
                .ip(db.getIp())
                .port(db.getPort())
                .dbName(db.getDbName())
                .dbType(db.getDbType() != null ? db.getDbType().name() : null)
                .userName(db.getUserName())
                // Password intentionally excluded for security
                .build();
    }

    private Loader toEntity(EtlLoaderDto d) {
        // Fetch source database
        SourceDatabase sourceDb = null;
        if (d.getSourceDatabaseId() != null) {
            sourceDb = sourceDbRepo.findById(d.getSourceDatabaseId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.VALIDATION_INVALID_VALUE,
                            "Source database with ID " + d.getSourceDatabaseId() + " not found",
                            "sourceDatabaseId"
                    ));
        }

        // Parse purge strategy
        PurgeStrategy purgeStrategy = PurgeStrategy.FAIL_ON_DUPLICATE; // default
        if (d.getPurgeStrategy() != null && !d.getPurgeStrategy().isBlank()) {
            try {
                purgeStrategy = PurgeStrategy.valueOf(d.getPurgeStrategy());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_INVALID_VALUE,
                        "Invalid purge strategy: " + d.getPurgeStrategy() + ". Must be one of: FAIL_ON_DUPLICATE, PURGE_AND_RELOAD, SKIP_DUPLICATES",
                        "purgeStrategy"
                );
            }
        }

        return Loader.builder()
                .id(d.getId())
                .loaderCode(d.getLoaderCode())
                .loaderSql(d.getLoaderSql())
                .minIntervalSeconds(d.getMinIntervalSeconds())
                .maxIntervalSeconds(d.getMaxIntervalSeconds())
                .maxQueryPeriodSeconds(d.getMaxQueryPeriodSeconds())
                .maxParallelExecutions(d.getMaxParallelExecutions())
                .enabled(d.getEnabled() != null ? d.getEnabled() : true)
                .sourceDatabase(sourceDb)
                .purgeStrategy(purgeStrategy)
                .sourceTimezoneOffsetHours(d.getSourceTimezoneOffsetHours() != null ? d.getSourceTimezoneOffsetHours() : 0)
                .aggregationPeriodSeconds(d.getAggregationPeriodSeconds())
                .build();
    }

    /**
     * Get operational statistics for loaders overview page.
     *
     * @return Loaders statistics DTO
     */
    public LoadersStatsDto getStats() {
        log.debug("Fetching loader statistics");

        List<Loader> allLoaders = repo.findAll();
        int total = allLoaders.size();
        long active = allLoaders.stream().filter(Loader::isEnabled).count();
        long paused = total - active;

        // TODO: In future, calculate failed loaders based on execution history
        int failed = 0;

        log.info("Loader stats - Total: {}, Active: {}, Paused: {}, Failed: {}",
                total, active, paused, failed);

        return LoadersStatsDto.builder()
                .total(total)
                .active((int) active)
                .paused((int) paused)
                .failed(failed)
                // TODO: Add trend calculation based on historical data
                .build();
    }

    /**
     * Get recent activity events for loaders overview page.
     *
     * @param limit Maximum number of events to return
     * @return List of activity events
     */
    public List<ActivityEventDto> getRecentActivity(int limit) {
        log.debug("Fetching recent activity (limit: {})", limit);

        // TODO: In future, fetch from execution history or audit log table
        // For now, return placeholder/mock data
        List<ActivityEventDto> events = new ArrayList<>();

        log.info("Retrieved {} activity events", events.size());
        return events;
    }

    // ==================== APPROVAL WORKFLOW ====================

    /**
     * Approves a pending loader.
     *
     * <p><b>Security:</b> Must be called by ADMIN role only (enforced at controller level).
     *
     * <p><b>State Transition:</b> PENDING_APPROVAL → APPROVED
     *
     * <p><b>Side Effects:</b>
     * <ul>
     *   <li>Sets approval_status = APPROVED</li>
     *   <li>Sets approved_by and approved_at</li>
     *   <li>Creates audit log entry</li>
     * </ul>
     *
     * @param loaderCode Loader code to approve
     * @param authentication Spring Security authentication (for admin username)
     * @param comments Optional admin comments explaining approval decision
     * @param ipAddress Optional IP address of admin (for security auditing)
     * @return Updated loader DTO
     * @throws BusinessException if loader not found or not in PENDING_APPROVAL status
     */
    @Transactional
    public EtlLoaderDto approveLoader(String loaderCode, Authentication authentication, String comments, String ipAddress) {
        MDC.put("loaderCode", loaderCode);
        String adminUsername = authentication != null ? authentication.getName() : "UNKNOWN";
        MDC.put("adminUser", adminUsername);

        try {
            log.info("Approving loader: {} by admin: {}", loaderCode, adminUsername);

            // Validation
            if (loaderCode == null || loaderCode.isBlank()) {
                throw new BusinessException(
                    ErrorCode.VALIDATION_REQUIRED_FIELD,
                    "Loader code is required",
                    "loaderCode"
                );
            }

            // Find loader
            Loader loader = repo.findByLoaderCode(loaderCode)
                .orElseThrow(() -> {
                    log.warn("Cannot approve: Loader not found: {}", loaderCode);
                    return new BusinessException(
                        ErrorCode.LOADER_NOT_FOUND,
                        "Cannot approve: Loader with code '" + loaderCode + "' not found"
                    );
                });

            // Verify current status
            if (loader.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
                log.warn("Cannot approve loader {} - current status is {} (expected PENDING_APPROVAL)",
                        loaderCode, loader.getApprovalStatus());
                throw new BusinessException(
                    ErrorCode.VALIDATION_INVALID_VALUE,
                    "Cannot approve loader: current status is " + loader.getApprovalStatus() +
                    " (expected PENDING_APPROVAL)",
                    "approvalStatus"
                );
            }

            // Record previous status for audit
            ApprovalStatus previousStatus = loader.getApprovalStatus();

            // Update loader approval status
            Instant now = Instant.now();
            loader.setApprovalStatus(ApprovalStatus.APPROVED);
            loader.setApprovedBy(adminUsername);
            loader.setApprovedAt(now);
            // Clear rejection fields if previously rejected
            loader.setRejectedBy(null);
            loader.setRejectedAt(null);
            loader.setRejectionReason(null);

            Loader saved = repo.save(loader);

            // Create audit log entry
            createAuditLog(
                saved,
                ApprovalActionType.APPROVED,
                adminUsername,
                previousStatus,
                ApprovalStatus.APPROVED,
                null, // no rejection reason
                comments,
                ipAddress,
                null // TODO: Extract user agent from HTTP request
            );

            log.info("Loader approved successfully: {} by admin: {}", loaderCode, adminUsername);
            return toDto(saved);

        } finally {
            MDC.remove("loaderCode");
            MDC.remove("adminUser");
        }
    }

    /**
     * Rejects a pending loader.
     *
     * <p><b>Security:</b> Must be called by ADMIN role only (enforced at controller level).
     *
     * <p><b>State Transition:</b> PENDING_APPROVAL → REJECTED
     *
     * <p><b>Side Effects:</b>
     * <ul>
     *   <li>Sets approval_status = REJECTED</li>
     *   <li>Sets rejected_by, rejected_at, and rejection_reason</li>
     *   <li>Automatically disables loader (enabled = false)</li>
     *   <li>Creates audit log entry</li>
     * </ul>
     *
     * @param loaderCode Loader code to reject
     * @param rejectionReason Reason for rejection (required, helps loader creator fix issues)
     * @param authentication Spring Security authentication (for admin username)
     * @param comments Optional admin comments with additional guidance
     * @param ipAddress Optional IP address of admin (for security auditing)
     * @return Updated loader DTO
     * @throws BusinessException if loader not found, not in PENDING_APPROVAL status, or rejection reason missing
     */
    @Transactional
    public EtlLoaderDto rejectLoader(String loaderCode, String rejectionReason, Authentication authentication,
                                     String comments, String ipAddress) {
        MDC.put("loaderCode", loaderCode);
        String adminUsername = authentication != null ? authentication.getName() : "UNKNOWN";
        MDC.put("adminUser", adminUsername);

        try {
            log.info("Rejecting loader: {} by admin: {}", loaderCode, adminUsername);

            // Validation
            if (loaderCode == null || loaderCode.isBlank()) {
                throw new BusinessException(
                    ErrorCode.VALIDATION_REQUIRED_FIELD,
                    "Loader code is required",
                    "loaderCode"
                );
            }

            if (rejectionReason == null || rejectionReason.isBlank()) {
                throw new BusinessException(
                    ErrorCode.VALIDATION_REQUIRED_FIELD,
                    "Rejection reason is required",
                    "rejectionReason"
                );
            }

            // Find loader
            Loader loader = repo.findByLoaderCode(loaderCode)
                .orElseThrow(() -> {
                    log.warn("Cannot reject: Loader not found: {}", loaderCode);
                    return new BusinessException(
                        ErrorCode.LOADER_NOT_FOUND,
                        "Cannot reject: Loader with code '" + loaderCode + "' not found"
                    );
                });

            // Verify current status
            if (loader.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
                log.warn("Cannot reject loader {} - current status is {} (expected PENDING_APPROVAL)",
                        loaderCode, loader.getApprovalStatus());
                throw new BusinessException(
                    ErrorCode.VALIDATION_INVALID_VALUE,
                    "Cannot reject loader: current status is " + loader.getApprovalStatus() +
                    " (expected PENDING_APPROVAL)",
                    "approvalStatus"
                );
            }

            // Record previous status for audit
            ApprovalStatus previousStatus = loader.getApprovalStatus();

            // Update loader approval status
            Instant now = Instant.now();
            loader.setApprovalStatus(ApprovalStatus.REJECTED);
            loader.setRejectedBy(adminUsername);
            loader.setRejectedAt(now);
            loader.setRejectionReason(rejectionReason);
            // Clear approval fields if previously approved
            loader.setApprovedBy(null);
            loader.setApprovedAt(null);
            // Disable loader to prevent execution
            loader.setEnabled(false);

            Loader saved = repo.save(loader);

            // Create audit log entry
            createAuditLog(
                saved,
                ApprovalActionType.REJECTED,
                adminUsername,
                previousStatus,
                ApprovalStatus.REJECTED,
                rejectionReason,
                comments,
                ipAddress,
                null // TODO: Extract user agent from HTTP request
            );

            log.info("Loader rejected successfully: {} by admin: {} | reason: {}",
                    loaderCode, adminUsername, rejectionReason);
            return toDto(saved);

        } finally {
            MDC.remove("loaderCode");
            MDC.remove("adminUser");
        }
    }

    /**
     * Get approval audit trail for a specific loader.
     *
     * @param loaderCode Loader code
     * @return List of audit log entries ordered by timestamp descending
     */
    public List<ApprovalAuditLog> getApprovalHistory(String loaderCode) {
        log.debug("Fetching approval history for loader: {}", loaderCode);
        return auditLogRepo.findByLoaderCodeOrderByActionTimestampDesc(loaderCode);
    }

    /**
     * Creates an audit log entry for approval workflow actions.
     *
     * @param loader The loader entity
     * @param actionType Type of approval action
     * @param adminUsername Username of admin performing action
     * @param previousStatus Previous approval status
     * @param newStatus New approval status
     * @param rejectionReason Rejection reason (if applicable)
     * @param comments Admin comments
     * @param ipAddress Admin IP address
     * @param userAgent User agent string
     */
    private void createAuditLog(Loader loader, ApprovalActionType actionType, String adminUsername,
                                ApprovalStatus previousStatus, ApprovalStatus newStatus,
                                String rejectionReason, String comments, String ipAddress, String userAgent) {
        ApprovalAuditLog auditLog = ApprovalAuditLog.builder()
                .loaderId(loader.getId())
                .loaderCode(loader.getLoaderCode())
                .actionType(actionType)
                .adminUsername(adminUsername)
                .actionTimestamp(Instant.now())
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .rejectionReason(rejectionReason)
                .adminComments(comments)
                .adminIpAddress(ipAddress)
                .userAgent(userAgent)
                .loaderSqlSnapshot(loader.getLoaderSql()) // Store encrypted SQL snapshot
                .build();

        auditLogRepo.save(auditLog);
        log.debug("Audit log entry created for loader {} - action: {}", loader.getLoaderCode(), actionType);
    }

}
