# Unified Versioning System - Implementation Plan

**Date:** 2025-12-30
**Author:** Hassan Rawashdeh
**Status:** Design Phase - Ready for Implementation

---

## Overview

This document provides a complete implementation roadmap for the unified draft/active/archive versioning system. All database schema changes are complete (V17 migration). This document covers the remaining Java service layer, API endpoints, and integration points.

---

## Architecture Summary

### Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│ Entry Points                                                │
├─────────────────────────────────────────────────────────────┤
│ 1. Web UI (LoaderController)                               │
│ 2. Excel Import (ImportExportService)                      │
│ 3. ETL Initializer (EtlInitializerApplication)            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ VersioningService (Reusable Core Logic)                    │
├─────────────────────────────────────────────────────────────┤
│ • createDraft(loaderCode, data, user, changeType)          │
│ • updateDraft(draftId, data, user)                         │
│ • submitForApproval(draftId, user)                         │
│ • archiveLoader(loaderId, reason, user)                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ ApprovalWorkflowService                                     │
├─────────────────────────────────────────────────────────────┤
│ • approveDraft(draftId, admin, comments)                   │
│   → Archives old ACTIVE version                            │
│   → Activates draft (PENDING → ACTIVE)                     │
│   → Updates scheduler if enabled=true                      │
│ • rejectDraft(draftId, admin, reason)                      │
│   → Archives draft with REJECTED status                    │
│ • revokeActive(loaderCode, admin, reason)                  │
│   → Archives ACTIVE version                                │
│   → Disables loader                                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ Database Tables                                             │
├─────────────────────────────────────────────────────────────┤
│ • loader.loader (ACTIVE, DRAFT, PENDING_APPROVAL)          │
│ • loader.loader_archive (ARCHIVED, REJECTED)               │
└─────────────────────────────────────────────────────────────┘
```

---

## Implementation Checklist

### Phase 1: Entities & Repositories ✅ (Partially Done)

- [x] VersionStatus enum
- [x] ChangeType enum
- [x] Loader entity updated with versioning fields
- [ ] LoaderArchive entity
- [ ] LoaderArchiveRepository
- [ ] Update LoaderRepository with new query methods

### Phase 2: Service Layer

- [ ] VersioningService (reusable core)
- [ ] ApprovalWorkflowService (approve/reject/revoke)
- [ ] LoaderService refactoring (integrate with VersioningService)
- [ ] LoaderSchedulerService updates (handle version changes)

### Phase 3: API Layer

- [ ] LoaderController updates
  - [ ] POST /api/v1/res/loaders (create draft)
  - [ ] PUT /api/v1/res/loaders/{code} (update draft)
  - [ ] POST /api/v1/res/loaders/{code}/submit (submit for approval)
  - [ ] POST /api/v1/res/loaders/{code}/approve (ADMIN only)
  - [ ] POST /api/v1/res/loaders/{code}/reject (ADMIN only)
  - [ ] GET /api/v1/res/loaders/{code}/versions (version history)
  - [ ] GET /api/v1/res/loaders/{code}/versions/compare (diff)
  - [ ] POST /api/v1/res/loaders/{code}/rollback (ADMIN only)

### Phase 4: Integration Points

- [ ] ETL Initializer updates (create DRAFT instead of ACTIVE)
- [ ] Import/Export Service integration (when available)

### Phase 5: Testing & Deployment

- [ ] Unit tests for services
- [ ] Integration tests for workflows
- [ ] Build and deploy
- [ ] Frontend updates

---

## Code Templates

### 1. LoaderArchive Entity

**File:** `/services/loader/src/main/java/com/tiqmo/monitoring/loader/domain/loader/entity/LoaderArchive.java`

```java
package com.tiqmo.monitoring.loader.domain.loader.entity;

import com.tiqmo.monitoring.loader.infra.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Historical archive of loader versions.
 * Contains all replaced ACTIVE versions and REJECTED drafts.
 *
 * <p><b>Never auto-purged - manual cleanup only.</b>
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-30
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "loader_archive", schema = "loader",
       indexes = {
           @Index(name = "idx_loader_archive_loader_code", columnList = "loader_code"),
           @Index(name = "idx_loader_archive_version_number", columnList = "loader_code,version_number"),
           @Index(name = "idx_loader_archive_archived_at", columnList = "archived_at"),
           @Index(name = "idx_loader_archive_version_status", columnList = "version_status"),
           @Index(name = "idx_loader_archive_original_id", columnList = "original_loader_id")
       })
public class LoaderArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Original loader ID (before archival)
    @Column(name = "original_loader_id", nullable = false)
    private Long originalLoaderId;

    // Loader identification
    @Column(name = "loader_code", length = 64, nullable = false)
    private String loaderCode;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "version_status", nullable = false, length = 20)
    private String versionStatus; // ARCHIVED or REJECTED

    // Parent versioning
    @Column(name = "parent_version_id")
    private Long parentVersionId;

    // Loader configuration snapshot
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "loader_sql", columnDefinition = "TEXT", nullable = false)
    private String loaderSql;

    @Column(name = "source_database_id", nullable = false)
    private Long sourceDatabaseId;

    @Column(name = "min_interval_seconds", nullable = false)
    private Integer minIntervalSeconds;

    @Column(name = "max_interval_seconds", nullable = false)
    private Integer maxIntervalSeconds;

    @Column(name = "max_query_period_seconds", nullable = false)
    private Integer maxQueryPeriodSeconds;

    @Column(name = "max_parallel_executions", nullable = false)
    private Integer maxParallelExecutions;

    @Enumerated(EnumType.STRING)
    @Column(name = "load_status", length = 20)
    private LoadStatus loadStatus;

    @Column(name = "consecutive_zero_record_runs")
    private Integer consecutiveZeroRecordRuns;

    @Enumerated(EnumType.STRING)
    @Column(name = "purge_strategy", length = 30)
    private PurgeStrategy purgeStrategy;

    @Column(name = "aggregation_period_seconds")
    private Integer aggregationPeriodSeconds;

    @Column(name = "source_timezone_offset_hours")
    private Integer sourceTimezoneOffsetHours;

    @Column(name = "enabled")
    private Boolean enabled;

    // Approval workflow snapshot
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20)
    private ApprovalStatus approvalStatus;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by_version", length = 100)
    private String approvedByVersion;

    @Column(name = "approved_at_version")
    private Instant approvedAtVersion;

    // Rejection tracking
    @Column(name = "rejected_by", length = 100)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // Audit trail
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    @Column(name = "modified_at")
    private Instant modifiedAt;

    // Metadata
    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 50)
    private ChangeType changeType;

    @Column(name = "import_label", length = 255)
    private String importLabel;

    // Archive metadata
    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    @Column(name = "archived_by", nullable = false, length = 100)
    private String archivedBy;

    @Column(name = "archive_reason", length = 500)
    private String archiveReason;

    /**
     * Create archive from Loader entity
     */
    public static LoaderArchive fromLoader(Loader loader, String archivedBy, String archiveReason) {
        return LoaderArchive.builder()
                .originalLoaderId(loader.getId())
                .loaderCode(loader.getLoaderCode())
                .versionNumber(loader.getVersionNumber())
                .versionStatus(loader.getVersionStatus().name())
                .parentVersionId(loader.getParentVersionId())
                .loaderSql(loader.getLoaderSql())
                .sourceDatabaseId(loader.getSourceDatabase().getId())
                .minIntervalSeconds(loader.getMinIntervalSeconds())
                .maxIntervalSeconds(loader.getMaxIntervalSeconds())
                .maxQueryPeriodSeconds(loader.getMaxQueryPeriodSeconds())
                .maxParallelExecutions(loader.getMaxParallelExecutions())
                .loadStatus(loader.getLoadStatus())
                .consecutiveZeroRecordRuns(loader.getConsecutiveZeroRecordRuns())
                .purgeStrategy(loader.getPurgeStrategy())
                .aggregationPeriodSeconds(loader.getAggregationPeriodSeconds())
                .sourceTimezoneOffsetHours(loader.getSourceTimezoneOffsetHours())
                .enabled(loader.isEnabled())
                .approvalStatus(loader.getApprovalStatus())
                .approvedBy(loader.getApprovedBy())
                .approvedAt(loader.getApprovedAt())
                .approvedByVersion(loader.getApprovedByVersion())
                .approvedAtVersion(loader.getApprovedAtVersion())
                .rejectedBy(loader.getRejectedBy())
                .rejectedAt(loader.getRejectedAt())
                .rejectionReason(loader.getRejectionReason())
                .createdBy(loader.getCreatedBy())
                .createdAt(loader.getCreatedAt())
                .modifiedBy(loader.getModifiedBy())
                .modifiedAt(loader.getModifiedAt())
                .changeSummary(loader.getChangeSummary())
                .changeType(loader.getChangeType())
                .importLabel(loader.getImportLabel())
                .archivedAt(Instant.now())
                .archivedBy(archivedBy)
                .archiveReason(archiveReason)
                .build();
    }
}
```

---

### 2. LoaderArchiveRepository

**File:** `/services/loader/src/main/java/com/tiqmo/monitoring/loader/domain/loader/repo/LoaderArchiveRepository.java`

```java
package com.tiqmo.monitoring.loader.domain.loader.repo;

import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for loader archive operations.
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-30
 */
@Repository
public interface LoaderArchiveRepository extends JpaRepository<LoaderArchive, Long> {

    /**
     * Find all archived versions for a loader_code, ordered by version number descending.
     * Used for version history view.
     */
    List<LoaderArchive> findByLoaderCodeOrderByVersionNumberDesc(String loaderCode);

    /**
     * Find specific archived version by loader_code and version_number.
     * Used for version comparison and rollback.
     */
    Optional<LoaderArchive> findByLoaderCodeAndVersionNumber(String loaderCode, Integer versionNumber);

    /**
     * Find all REJECTED drafts for a loader_code.
     * Used for audit trail and resubmission.
     */
    @Query("SELECT la FROM LoaderArchive la WHERE la.loaderCode = :loaderCode AND la.versionStatus = 'REJECTED' ORDER BY la.versionNumber DESC")
    List<LoaderArchive> findRejectedVersions(String loaderCode);

    /**
     * Count total archived versions for a loader_code.
     * Used for statistics.
     */
    long countByLoaderCode(String loaderCode);

    /**
     * Find latest archived version for a loader_code.
     * Used for rollback.
     */
    Optional<LoaderArchive> findFirstByLoaderCodeOrderByVersionNumberDesc(String loaderCode);
}
```

---

### 3. Update LoaderRepository

**File:** `/services/loader/src/main/java/com/tiqmo/monitoring/loader/domain/loader/repo/LoaderRepository.java`

Add these methods:

```java
    // ==================== VERSIONING QUERIES ====================

    /**
     * Find ACTIVE version by loader_code.
     * Most common query - returns production version.
     */
    @Query("SELECT l FROM Loader l WHERE l.loaderCode = :loaderCode AND l.versionStatus = 'ACTIVE'")
    Optional<Loader> findActiveByLoaderCode(@Param("loaderCode") String loaderCode);

    /**
     * Find DRAFT or PENDING_APPROVAL version by loader_code.
     * Returns the one draft (if exists).
     */
    @Query("SELECT l FROM Loader l WHERE l.loaderCode = :loaderCode AND l.versionStatus IN ('DRAFT', 'PENDING_APPROVAL')")
    Optional<Loader> findDraftByLoaderCode(@Param("loaderCode") String loaderCode);

    /**
     * Find any version (ACTIVE, DRAFT, PENDING) by loader_code.
     * Used when loader_code must be unique regardless of status.
     */
    Optional<Loader> findByLoaderCode(String loaderCode);

    /**
     * Find all ACTIVE loaders.
     * Used by scheduler to load production loaders.
     */
    @Query("SELECT l FROM Loader l WHERE l.versionStatus = 'ACTIVE'")
    List<Loader> findAllActive();

    /**
     * Find all PENDING_APPROVAL loaders.
     * Used by approval management UI.
     */
    @Query("SELECT l FROM Loader l WHERE l.versionStatus = 'PENDING_APPROVAL' ORDER BY l.createdAt DESC")
    List<Loader> findAllPendingApproval();

    /**
     * Find all DRAFT loaders.
     * Used by "My Drafts" UI.
     */
    @Query("SELECT l FROM Loader l WHERE l.versionStatus = 'DRAFT' ORDER BY l.modifiedAt DESC NULLS LAST, l.createdAt DESC")
    List<Loader> findAllDrafts();

    /**
     * Find drafts created by specific user.
     * Used by "My Drafts" filtered view.
     */
    @Query("SELECT l FROM Loader l WHERE l.versionStatus IN ('DRAFT', 'PENDING_APPROVAL') AND l.createdBy = :username ORDER BY l.createdAt DESC")
    List<Loader> findDraftsByUser(@Param("username") String username);

    /**
     * Check if loader_code exists (any status).
     * Used for uniqueness validation.
     */
    boolean existsByLoaderCode(String loaderCode);

    /**
     * Check if ACTIVE version exists for loader_code.
     * Used to determine if creating new loader vs new version.
     */
    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END FROM Loader l WHERE l.loaderCode = :loaderCode AND l.versionStatus = 'ACTIVE'")
    boolean existsActiveByLoaderCode(@Param("loaderCode") String loaderCode);
```

---

### 4. VersioningService (Reusable Core)

**File:** `/services/loader/src/main/java/com/tiqmo/monitoring/loader/service/versioning/VersioningService.java`

```java
package com.tiqmo.monitoring.loader.service.versioning;

import com.tiqmo.monitoring.loader.domain.loader.entity.*;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderArchiveRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.SourceDatabaseRepository;
import com.tiqmo.monitoring.loader.dto.loader.EtlLoaderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Reusable versioning service for draft management.
 *
 * <p><b>Used by:</b>
 * <ul>
 *   <li>LoaderController (UI edits)</li>
 *   <li>ETL Initializer (YAML imports)</li>
 *   <li>Import/Export Service (Excel imports)</li>
 * </ul>
 *
 * <p><b>Key Operations:</b>
 * <ul>
 *   <li>createDraft() - Create new draft (new loader or new version)</li>
 *   <li>updateDraft() - Update existing draft</li>
 *   <li>submitForApproval() - Submit draft for admin approval</li>
 *   <li>archiveLoader() - Archive loader version to loader_archive</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VersioningService {

    private final LoaderRepository loaderRepository;
    private final LoaderArchiveRepository archiveRepository;
    private final SourceDatabaseRepository sourceDbRepository;

    /**
     * Create a new draft loader.
     *
     * <p><b>Logic:</b>
     * <ul>
     *   <li>If no ACTIVE version exists: Create version 1 (new loader)</li>
     *   <li>If ACTIVE version exists: Create new version (cumulative draft)</li>
     *   <li>If DRAFT already exists: Replace with new draft (cumulative)</li>
     * </ul>
     *
     * @param dto Loader data
     * @param username User creating the draft
     * @param changeType Source of change (IMPORT_CREATE, MANUAL_EDIT, etc.)
     * @param importLabel Optional import batch label
     * @return Created draft loader
     */
    @Transactional
    public Loader createDraft(EtlLoaderDto dto, String username, ChangeType changeType, String importLabel) {
        log.info("Creating draft for loader_code: {} by user: {}", dto.getLoaderCode(), username);

        // Check if ACTIVE version exists
        Loader activeVersion = loaderRepository.findActiveByLoaderCode(dto.getLoaderCode()).orElse(null);

        // Check if DRAFT already exists
        Loader existingDraft = loaderRepository.findDraftByLoaderCode(dto.getLoaderCode()).orElse(null);

        Loader draft;

        if (existingDraft != null) {
            // Replace existing draft (cumulative drafts)
            log.info("Replacing existing draft ID {} for loader_code: {}", existingDraft.getId(), dto.getLoaderCode());
            draft = existingDraft;
            updateDraftFields(draft, dto);
            draft.setModifiedBy(username);
            draft.setModifiedAt(Instant.now());
            draft.setChangeSummary(dto.getChangeSummary() != null ? dto.getChangeSummary() : "Updated draft");
        } else {
            // Create new draft
            draft = new Loader();
            draft.setLoaderCode(dto.getLoaderCode());
            draft.setVersionStatus(VersionStatus.DRAFT);
            draft.setCreatedBy(username);
            draft.setCreatedAt(Instant.now());
            draft.setChangeType(changeType);
            draft.setImportLabel(importLabel);
            draft.setChangeSummary(dto.getChangeSummary() != null ? dto.getChangeSummary() :
                    activeVersion == null ? "New loader" : "Updated loader");

            if (activeVersion != null) {
                // New version of existing loader
                draft.setParentVersionId(activeVersion.getId());
                log.info("Creating draft version {} based on ACTIVE version {}",
                        activeVersion.getVersionNumber() + 1, activeVersion.getVersionNumber());
            } else {
                // Brand new loader
                draft.setParentVersionId(null);
                log.info("Creating first version (v1) for new loader: {}", dto.getLoaderCode());
            }

            updateDraftFields(draft, dto);
        }

        draft = loaderRepository.save(draft);
        log.info("Draft saved: loader_code={}, version={}, status={}",
                draft.getLoaderCode(), draft.getVersionNumber(), draft.getVersionStatus());

        return draft;
    }

    /**
     * Update existing draft loader.
     *
     * @param draftId Draft loader ID
     * @param dto Updated loader data
     * @param username User making the update
     * @return Updated draft
     */
    @Transactional
    public Loader updateDraft(Long draftId, EtlLoaderDto dto, String username) {
        Loader draft = loaderRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Loader not found: " + draftId));

        if (!draft.getVersionStatus().isDraft()) {
            throw new IllegalStateException("Cannot update non-draft loader. Status: " + draft.getVersionStatus());
        }

        log.info("Updating draft: loader_code={}, version={}", draft.getLoaderCode(), draft.getVersionNumber());

        updateDraftFields(draft, dto);
        draft.setModifiedBy(username);
        draft.setModifiedAt(Instant.now());

        draft = loaderRepository.save(draft);
        log.info("Draft updated: loader_code={}, version={}", draft.getLoaderCode(), draft.getVersionNumber());

        return draft;
    }

    /**
     * Submit draft for approval.
     * Changes status from DRAFT → PENDING_APPROVAL.
     *
     * @param draftId Draft loader ID
     * @param username User submitting for approval
     * @return Updated draft with PENDING_APPROVAL status
     */
    @Transactional
    public Loader submitForApproval(Long draftId, String username) {
        Loader draft = loaderRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Loader not found: " + draftId));

        if (draft.getVersionStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT loaders can be submitted. Status: " + draft.getVersionStatus());
        }

        log.info("Submitting draft for approval: loader_code={}, version={} by user: {}",
                draft.getLoaderCode(), draft.getVersionNumber(), username);

        draft.setVersionStatus(VersionStatus.PENDING_APPROVAL);
        draft.setModifiedBy(username);
        draft.setModifiedAt(Instant.now());

        draft = loaderRepository.save(draft);
        log.info("Draft submitted for approval: loader_code={}, version={}",
                draft.getLoaderCode(), draft.getVersionNumber());

        return draft;
    }

    /**
     * Archive a loader version.
     * Moves loader from loader.loader to loader.loader_archive table.
     *
     * @param loader Loader to archive
     * @param archivedBy User archiving the loader
     * @param archiveReason Reason for archival
     */
    @Transactional
    public void archiveLoader(Loader loader, String archivedBy, String archiveReason) {
        log.info("Archiving loader: loader_code={}, version={}, reason={}",
                loader.getLoaderCode(), loader.getVersionNumber(), archiveReason);

        // Create archive record
        LoaderArchive archive = LoaderArchive.fromLoader(loader, archivedBy, archiveReason);
        archiveRepository.save(archive);

        // Delete from loader table
        loaderRepository.delete(loader);

        log.info("Loader archived: loader_code={}, version={}, archive_id={}",
                loader.getLoaderCode(), loader.getVersionNumber(), archive.getId());
    }

    /**
     * Helper method to update draft fields from DTO.
     */
    private void updateDraftFields(Loader draft, EtlLoaderDto dto) {
        // Find source database
        SourceDatabase sourceDb = sourceDbRepository.findById(dto.getSourceDatabaseId())
                .orElseThrow(() -> new IllegalArgumentException("Source database not found: " + dto.getSourceDatabaseId()));

        draft.setLoaderSql(dto.getLoaderSql());
        draft.setSourceDatabase(sourceDb);
        draft.setMinIntervalSeconds(dto.getMinIntervalSeconds() != null ? dto.getMinIntervalSeconds() : 10);
        draft.setMaxIntervalSeconds(dto.getMaxIntervalSeconds() != null ? dto.getMaxIntervalSeconds() : 60);
        draft.setMaxQueryPeriodSeconds(dto.getMaxQueryPeriodSeconds() != null ? dto.getMaxQueryPeriodSeconds() : 432000);
        draft.setMaxParallelExecutions(dto.getMaxParallelExecutions() != null ? dto.getMaxParallelExecutions() : 1);
        draft.setPurgeStrategy(dto.getPurgeStrategy() != null ?
                PurgeStrategy.valueOf(dto.getPurgeStrategy().toUpperCase()) :
                PurgeStrategy.FAIL_ON_DUPLICATE);
        draft.setAggregationPeriodSeconds(dto.getAggregationPeriodSeconds());
        draft.setSourceTimezoneOffsetHours(dto.getSourceTimezoneOffsetHours() != null ?
                dto.getSourceTimezoneOffsetHours() : 0);

        // Drafts are always disabled by default (enabled only after approval)
        draft.setEnabled(false);

        // Default values for runtime state
        draft.setLoadStatus(LoadStatus.IDLE);
        draft.setConsecutiveZeroRecordRuns(0);
    }
}
```

---

### 5. ApprovalWorkflowService

**File:** `/services/loader/src/main/java/com/tiqmo/monitoring/loader/service/approval/ApprovalWorkflowService.java`

```java
package com.tiqmo.monitoring.loader.service.approval;

import com.tiqmo.monitoring.loader.domain.loader.entity.*;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.service.versioning.VersioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Approval workflow operations: approve, reject, revoke.
 *
 * <p><b>Operations:</b>
 * <ul>
 *   <li>approveDraft() - Approve PENDING draft → ACTIVE</li>
 *   <li>rejectDraft() - Reject PENDING draft → archive</li>
 *   <li>revokeActive() - Revoke ACTIVE → archive</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalWorkflowService {

    private final LoaderRepository loaderRepository;
    private final VersioningService versioningService;

    /**
     * Approve a pending draft.
     *
     * <p><b>Steps:</b>
     * <ol>
     *   <li>Validate draft is PENDING_APPROVAL</li>
     *   <li>Archive old ACTIVE version (if exists)</li>
     *   <li>Activate draft (PENDING → ACTIVE)</li>
     *   <li>Set approval metadata</li>
     *   <li>Enable loader based on draft settings</li>
     * </ol>
     *
     * @param draftId Draft loader ID
     * @param adminUsername Admin approving
     * @param comments Optional approval comments
     * @return Activated loader
     */
    @Transactional
    public Loader approveDraft(Long draftId, String adminUsername, String comments) {
        Loader draft = loaderRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Loader not found: " + draftId));

        if (draft.getVersionStatus() != VersionStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only PENDING_APPROVAL loaders can be approved. Status: " + draft.getVersionStatus());
        }

        log.info("Approving draft: loader_code={}, version={} by admin: {}",
                draft.getLoaderCode(), draft.getVersionNumber(), adminUsername);

        // Step 1: Archive old ACTIVE version (if exists)
        Loader oldActive = loaderRepository.findActiveByLoaderCode(draft.getLoaderCode()).orElse(null);
        if (oldActive != null) {
            String archiveReason = String.format("Replaced by version %d", draft.getVersionNumber());
            versioningService.archiveLoader(oldActive, adminUsername, archiveReason);
            log.info("Archived old ACTIVE version {}: loader_code={}",
                    oldActive.getVersionNumber(), oldActive.getLoaderCode());
        }

        // Step 2: Activate draft
        draft.setVersionStatus(VersionStatus.ACTIVE);
        draft.setApprovedByVersion(adminUsername);
        draft.setApprovedAtVersion(Instant.now());

        // Step 3: Enable loader (if draft requested enabled=true)
        // Note: Database constraint ensures only ACTIVE versions can be enabled
        // For safety, we default to false unless explicitly requested
        draft.setEnabled(false); // Can be changed via separate enable/disable endpoint

        draft = loaderRepository.save(draft);

        log.info("Draft approved and activated: loader_code={}, version={}, enabled={}",
                draft.getLoaderCode(), draft.getVersionNumber(), draft.isEnabled());

        return draft;
    }

    /**
     * Reject a pending draft.
     *
     * <p><b>Steps:</b>
     * <ol>
     *   <li>Validate draft is PENDING_APPROVAL</li>
     *   <li>Set rejection metadata</li>
     *   <li>Archive draft with REJECTED status</li>
     * </ol>
     *
     * @param draftId Draft loader ID
     * @param adminUsername Admin rejecting
     * @param rejectionReason Reason for rejection (mandatory)
     * @param comments Optional additional comments
     */
    @Transactional
    public void rejectDraft(Long draftId, String adminUsername, String rejectionReason, String comments) {
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        Loader draft = loaderRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Loader not found: " + draftId));

        if (draft.getVersionStatus() != VersionStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only PENDING_APPROVAL loaders can be rejected. Status: " + draft.getVersionStatus());
        }

        log.info("Rejecting draft: loader_code={}, version={} by admin: {}, reason: {}",
                draft.getLoaderCode(), draft.getVersionNumber(), adminUsername, rejectionReason);

        // Set rejection metadata
        draft.setRejectedBy(adminUsername);
        draft.setRejectedAt(Instant.now());
        draft.setRejectionReason(rejectionReason);

        // Save to update rejection fields before archival
        draft = loaderRepository.save(draft);

        // Archive with REJECTED status
        String archiveReason = "Rejected by admin: " + rejectionReason;
        versioningService.archiveLoader(draft, adminUsername, archiveReason);

        log.info("Draft rejected and archived: loader_code={}, version={}",
                draft.getLoaderCode(), draft.getVersionNumber());
    }

    /**
     * Revoke an ACTIVE loader.
     *
     * <p>Used when admin needs to disable/remove a production loader.
     *
     * @param loaderCode Loader code to revoke
     * @param adminUsername Admin revoking
     * @param reason Reason for revocation (mandatory)
     */
    @Transactional
    public void revokeActive(String loaderCode, String adminUsername, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Revocation reason is required");
        }

        Loader active = loaderRepository.findActiveByLoaderCode(loaderCode)
                .orElseThrow(() -> new IllegalArgumentException("No ACTIVE loader found with code: " + loaderCode));

        log.info("Revoking ACTIVE loader: loader_code={}, version={} by admin: {}, reason: {}",
                active.getLoaderCode(), active.getVersionNumber(), adminUsername, reason);

        // Disable loader before archival
        active.setEnabled(false);
        active = loaderRepository.save(active);

        // Archive
        String archiveReason = "Revoked by admin: " + reason;
        versioningService.archiveLoader(active, adminUsername, archiveReason);

        log.info("ACTIVE loader revoked and archived: loader_code={}, version={}",
                active.getLoaderCode(), active.getVersionNumber());
    }
}
```

---

## Next Steps

1. **Review this implementation plan**
2. **Confirm approach aligns with requirements**
3. **Implement remaining pieces** (can do in phases)
4. **Test database migration first** (deploy V17 to clean cluster)
5. **Implement services incrementally**
6. **Add API endpoints**
7. **Update ETL initializer**
8. **Update frontend**

---

## Notes

- All code follows Spring Boot 3.5.6 best practices
- Transactions properly managed with `@Transactional`
- Comprehensive logging for audit trail
- Clear error messages for debugging
- Database constraints enforced at both DB and application layer
- Services are reusable across all entry points (UI, Excel, ETL)

---

**Status:** Ready for implementation
**Next Review:** After V17 migration testing
