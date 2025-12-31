# Revised Service Architecture - Modular Design

**Date:** 2025-12-30
**Author:** Hassan Rawashdeh
**Design Principle:** Single Responsibility + Reusability

---

## Service Breakdown

Instead of 2 large services (VersioningService + ApprovalWorkflowService), we'll create **6 focused services**:

### 1. **LoaderDraftService** - Draft Management
**Responsibility:** Create and update drafts
**Methods:**
- `createDraft(dto, user, changeType, importLabel)` - Create new draft
- `updateDraft(draftId, dto, user)` - Update existing draft
- `getDraft(loaderCode)` - Get draft by loader_code
- `deleteDraft(draftId, user)` - Delete draft (before submission)

**Used by:** LoaderController, ETL Initializer, Import/Export Service

---

### 2. **LoaderArchiveService** - Archival Operations
**Responsibility:** Move loaders to/from archive table
**Methods:**
- `archiveLoader(loader, archivedBy, reason)` - Archive loader to loader_archive
- `getArchivedVersions(loaderCode)` - Get all archived versions
- `getArchivedVersion(loaderCode, versionNumber)` - Get specific version
- `countArchivedVersions(loaderCode)` - Count versions

**Used by:** ApprovalService, RollbackService, VersionHistoryService

---

### 3. **LoaderApprovalService** - Approval Operations
**Responsibility:** Approve drafts (PENDING → ACTIVE)
**Methods:**
- `approveDraft(draftId, admin, comments)` - Approve draft
  - Calls LoaderArchiveService to archive old ACTIVE
  - Activates draft
  - Updates approval metadata

**Used by:** LoaderController (approve endpoint)

---

### 4. **LoaderRejectionService** - Rejection Operations
**Responsibility:** Reject drafts (PENDING → ARCHIVED)
**Methods:**
- `rejectDraft(draftId, admin, reason, comments)` - Reject draft
  - Sets rejection metadata
  - Calls LoaderArchiveService to archive with REJECTED status

**Used by:** LoaderController (reject endpoint)

---

### 5. **LoaderRevocationService** - Revocation Operations
**Responsibility:** Revoke ACTIVE loaders (ACTIVE → ARCHIVED)
**Methods:**
- `revokeActive(loaderCode, admin, reason)` - Revoke active loader
  - Disables loader
  - Calls LoaderArchiveService to archive

**Used by:** LoaderController (revoke endpoint)

---

### 6. **LoaderVersionHistoryService** - Version Queries
**Responsibility:** Query version history and comparison
**Methods:**
- `getVersionHistory(loaderCode)` - Get all versions (current + archived)
- `compareVersions(loaderCode, v1, v2)` - Compare two versions
- `getActiveVersion(loaderCode)` - Get current ACTIVE version
- `getDraftVersion(loaderCode)` - Get current DRAFT version

**Used by:** LoaderController (history/comparison endpoints), UI

---

### 7. **LoaderRollbackService** - Rollback Operations
**Responsibility:** Restore archived versions as drafts
**Methods:**
- `rollbackToVersion(loaderCode, versionNumber, admin, reason)` - Create draft from archived version
  - Fetches archived version
  - Creates new draft with ChangeType.ROLLBACK
  - Calls LoaderDraftService to create draft

**Used by:** LoaderController (rollback endpoint, ADMIN only)

---

## Service Dependency Graph

```
┌─────────────────────────────────────────────────────────────────┐
│ Controller Layer (LoaderController)                             │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
┌───────────────────┐ ┌──────────────┐ ┌──────────────────┐
│LoaderDraftService │ │LoaderApproval│ │LoaderVersionHistory│
│                   │ │   Service    │ │    Service        │
└───────────────────┘ └──────────────┘ └──────────────────┘
         │                    │                   │
         │                    ▼                   │
         │          ┌──────────────────┐          │
         └─────────>│LoaderArchive     │<─────────┘
                    │   Service        │
                    └──────────────────┘
                              ▲
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
┌───────────────────┐ ┌──────────────┐ ┌──────────────────┐
│LoaderRejection    │ │LoaderRevoc   │ │LoaderRollback    │
│   Service         │ │ationService  │ │   Service        │
└───────────────────┘ └──────────────┘ └──────────────────┘
```

**Key Points:**
- All archival operations go through `LoaderArchiveService` (single point of control)
- Services depend on each other, but no circular dependencies
- Each service has clear, testable responsibility
- Easy to mock for unit testing

---

## Implementation Templates

### 1. LoaderDraftService

**File:** `/services/loader/src/main/java/com/tiqmo/monitoring/loader/service/versioning/LoaderDraftService.java`

```java
package com.tiqmo.monitoring.loader.service.versioning;

import com.tiqmo.monitoring.loader.domain.loader.entity.*;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.SourceDatabaseRepository;
import com.tiqmo.monitoring.loader.dto.loader.EtlLoaderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for managing loader drafts (create, update, delete).
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoaderDraftService {

    private final LoaderRepository loaderRepository;
    private final SourceDatabaseRepository sourceDbRepository;

    /**
     * Create a new draft.
     * If ACTIVE exists → new version (cumulative draft)
     * If DRAFT exists → replace existing draft (cumulative)
     * If neither exists → version 1
     */
    @Transactional
    public Loader createDraft(EtlLoaderDto dto, String username, ChangeType changeType, String importLabel) {
        log.info("Creating draft: loader_code={}, user={}, changeType={}",
                dto.getLoaderCode(), username, changeType);

        // Check for existing versions
        Loader activeVersion = loaderRepository.findActiveByLoaderCode(dto.getLoaderCode()).orElse(null);
        Loader existingDraft = loaderRepository.findDraftByLoaderCode(dto.getLoaderCode()).orElse(null);

        Loader draft;

        if (existingDraft != null) {
            // Replace existing draft (cumulative)
            log.info("Replacing existing draft ID {} for loader_code: {}",
                    existingDraft.getId(), dto.getLoaderCode());
            draft = existingDraft;
            updateDraftFields(draft, dto);
            draft.setModifiedBy(username);
            draft.setModifiedAt(Instant.now());
        } else {
            // Create new draft
            draft = new Loader();
            draft.setLoaderCode(dto.getLoaderCode());
            draft.setVersionStatus(VersionStatus.DRAFT);
            draft.setCreatedBy(username);
            draft.setCreatedAt(Instant.now());
            draft.setChangeType(changeType);
            draft.setImportLabel(importLabel);
            draft.setParentVersionId(activeVersion != null ? activeVersion.getId() : null);
            updateDraftFields(draft, dto);
        }

        draft.setChangeSummary(dto.getChangeSummary() != null ? dto.getChangeSummary() :
                activeVersion == null ? "New loader" : "Updated loader");

        draft = loaderRepository.save(draft);
        log.info("Draft created: loader_code={}, version={}", draft.getLoaderCode(), draft.getVersionNumber());

        return draft;
    }

    /**
     * Update existing draft.
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
        draft.setChangeSummary(dto.getChangeSummary());

        return loaderRepository.save(draft);
    }

    /**
     * Submit draft for approval (DRAFT → PENDING_APPROVAL).
     */
    @Transactional
    public Loader submitForApproval(Long draftId, String username) {
        Loader draft = loaderRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Loader not found: " + draftId));

        if (draft.getVersionStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT loaders can be submitted. Status: " + draft.getVersionStatus());
        }

        log.info("Submitting draft for approval: loader_code={}, version={}",
                draft.getLoaderCode(), draft.getVersionNumber());

        draft.setVersionStatus(VersionStatus.PENDING_APPROVAL);
        draft.setModifiedBy(username);
        draft.setModifiedAt(Instant.now());

        return loaderRepository.save(draft);
    }

    /**
     * Delete draft (only before submission).
     */
    @Transactional
    public void deleteDraft(Long draftId, String username) {
        Loader draft = loaderRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Loader not found: " + draftId));

        if (draft.getVersionStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT loaders can be deleted. Status: " + draft.getVersionStatus());
        }

        log.info("Deleting draft: loader_code={}, version={} by user: {}",
                draft.getLoaderCode(), draft.getVersionNumber(), username);

        loaderRepository.delete(draft);
    }

    /**
     * Get draft by loader_code.
     */
    @Transactional(readOnly = true)
    public Loader getDraft(String loaderCode) {
        return loaderRepository.findDraftByLoaderCode(loaderCode).orElse(null);
    }

    // Helper method
    private void updateDraftFields(Loader draft, EtlLoaderDto dto) {
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
        draft.setEnabled(false); // Drafts always disabled
        draft.setLoadStatus(LoadStatus.IDLE);
        draft.setConsecutiveZeroRecordRuns(0);
    }
}
```

---

### 2. LoaderArchiveService

```java
package com.tiqmo.monitoring.loader.service.versioning;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderArchive;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderArchiveRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for archival operations.
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoaderArchiveService {

    private final LoaderArchiveRepository archiveRepository;
    private final LoaderRepository loaderRepository;

    /**
     * Archive a loader (move from loader table to loader_archive).
     */
    @Transactional
    public LoaderArchive archiveLoader(Loader loader, String archivedBy, String archiveReason) {
        log.info("Archiving loader: loader_code={}, version={}, reason={}",
                loader.getLoaderCode(), loader.getVersionNumber(), archiveReason);

        // Create archive record
        LoaderArchive archive = LoaderArchive.fromLoader(loader, archivedBy, archiveReason);
        archive = archiveRepository.save(archive);

        // Delete from loader table
        loaderRepository.delete(loader);

        log.info("Loader archived: loader_code={}, version={}, archive_id={}",
                loader.getLoaderCode(), loader.getVersionNumber(), archive.getId());

        return archive;
    }

    /**
     * Get all archived versions for a loader_code.
     */
    @Transactional(readOnly = true)
    public List<LoaderArchive> getArchivedVersions(String loaderCode) {
        return archiveRepository.findByLoaderCodeOrderByVersionNumberDesc(loaderCode);
    }

    /**
     * Get specific archived version.
     */
    @Transactional(readOnly = true)
    public Optional<LoaderArchive> getArchivedVersion(String loaderCode, Integer versionNumber) {
        return archiveRepository.findByLoaderCodeAndVersionNumber(loaderCode, versionNumber);
    }

    /**
     * Count archived versions.
     */
    @Transactional(readOnly = true)
    public long countArchivedVersions(String loaderCode) {
        return archiveRepository.countByLoaderCode(loaderCode);
    }

    /**
     * Get latest archived version.
     */
    @Transactional(readOnly = true)
    public Optional<LoaderArchive> getLatestArchivedVersion(String loaderCode) {
        return archiveRepository.findFirstByLoaderCodeOrderByVersionNumberDesc(loaderCode);
    }
}
```

---

### 3. LoaderApprovalService

```java
package com.tiqmo.monitoring.loader.service.approval;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.VersionStatus;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.service.versioning.LoaderArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for approving drafts (PENDING → ACTIVE).
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoaderApprovalService {

    private final LoaderRepository loaderRepository;
    private final LoaderArchiveService archiveService;

    /**
     * Approve a pending draft.
     * Archives old ACTIVE version, activates draft.
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

        // Archive old ACTIVE version (if exists)
        Loader oldActive = loaderRepository.findActiveByLoaderCode(draft.getLoaderCode()).orElse(null);
        if (oldActive != null) {
            String archiveReason = String.format("Replaced by version %d", draft.getVersionNumber());
            archiveService.archiveLoader(oldActive, adminUsername, archiveReason);
            log.info("Archived old ACTIVE version {}", oldActive.getVersionNumber());
        }

        // Activate draft
        draft.setVersionStatus(VersionStatus.ACTIVE);
        draft.setApprovedByVersion(adminUsername);
        draft.setApprovedAtVersion(Instant.now());
        draft.setEnabled(false); // Safety: disabled by default after approval

        draft = loaderRepository.save(draft);

        log.info("Draft approved and activated: loader_code={}, version={}",
                draft.getLoaderCode(), draft.getVersionNumber());

        return draft;
    }
}
```

---

### 4. LoaderRejectionService

```java
package com.tiqmo.monitoring.loader.service.approval;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.VersionStatus;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.service.versioning.LoaderArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for rejecting drafts (PENDING → ARCHIVED with REJECTED status).
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoaderRejectionService {

    private final LoaderRepository loaderRepository;
    private final LoaderArchiveService archiveService;

    /**
     * Reject a pending draft.
     * Sets rejection metadata and archives.
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
        draft = loaderRepository.save(draft);

        // Archive with REJECTED status
        String archiveReason = "Rejected by admin: " + rejectionReason;
        archiveService.archiveLoader(draft, adminUsername, archiveReason);

        log.info("Draft rejected and archived: loader_code={}, version={}",
                draft.getLoaderCode(), draft.getVersionNumber());
    }
}
```

---

### 5. LoaderRevocationService

```java
package com.tiqmo.monitoring.loader.service.approval;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.service.versioning.LoaderArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for revoking ACTIVE loaders (ACTIVE → ARCHIVED).
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoaderRevocationService {

    private final LoaderRepository loaderRepository;
    private final LoaderArchiveService archiveService;

    /**
     * Revoke an ACTIVE loader.
     * Disables and archives.
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
        archiveService.archiveLoader(active, adminUsername, archiveReason);

        log.info("ACTIVE loader revoked and archived: loader_code={}, version={}",
                active.getLoaderCode(), active.getVersionNumber());
    }
}
```

---

### 6. LoaderVersionHistoryService

```java
package com.tiqmo.monitoring.loader.service.versioning;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderArchive;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.dto.loader.LoaderVersionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for querying version history.
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoaderVersionHistoryService {

    private final LoaderRepository loaderRepository;
    private final LoaderArchiveService archiveService;

    /**
     * Get complete version history (current + archived).
     */
    @Transactional(readOnly = true)
    public List<LoaderVersionDto> getVersionHistory(String loaderCode) {
        List<LoaderVersionDto> history = new ArrayList<>();

        // Get current versions (ACTIVE, DRAFT, PENDING)
        Loader active = loaderRepository.findActiveByLoaderCode(loaderCode).orElse(null);
        if (active != null) {
            history.add(LoaderVersionDto.fromLoader(active));
        }

        Loader draft = loaderRepository.findDraftByLoaderCode(loaderCode).orElse(null);
        if (draft != null) {
            history.add(LoaderVersionDto.fromLoader(draft));
        }

        // Get archived versions
        List<LoaderArchive> archived = archiveService.getArchivedVersions(loaderCode);
        archived.forEach(archive -> history.add(LoaderVersionDto.fromArchive(archive)));

        // Sort by version number descending
        history.sort((a, b) -> b.getVersionNumber().compareTo(a.getVersionNumber()));

        return history;
    }

    /**
     * Get ACTIVE version.
     */
    @Transactional(readOnly = true)
    public Loader getActiveVersion(String loaderCode) {
        return loaderRepository.findActiveByLoaderCode(loaderCode).orElse(null);
    }

    /**
     * Get DRAFT version.
     */
    @Transactional(readOnly = true)
    public Loader getDraftVersion(String loaderCode) {
        return loaderRepository.findDraftByLoaderCode(loaderCode).orElse(null);
    }
}
```

---

### 7. LoaderRollbackService

```java
package com.tiqmo.monitoring.loader.service.versioning;

import com.tiqmo.monitoring.loader.domain.loader.entity.ChangeType;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderArchive;
import com.tiqmo.monitoring.loader.dto.loader.EtlLoaderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for rollback operations (restore archived versions as drafts).
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoaderRollbackService {

    private final LoaderArchiveService archiveService;
    private final LoaderDraftService draftService;

    /**
     * Rollback to an archived version.
     * Creates a new draft based on the archived version.
     */
    @Transactional
    public Loader rollbackToVersion(String loaderCode, Integer versionNumber, String adminUsername, String reason) {
        log.info("Rollback request: loader_code={}, target_version={} by admin: {}",
                loaderCode, versionNumber, adminUsername);

        // Fetch archived version
        LoaderArchive archivedVersion = archiveService.getArchivedVersion(loaderCode, versionNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Archived version not found: %s v%d", loaderCode, versionNumber)
                ));

        // Create DTO from archived version
        EtlLoaderDto dto = EtlLoaderDto.fromArchivedLoader(archivedVersion);
        dto.setChangeSummary(String.format("Rollback to version %d: %s", versionNumber, reason));

        // Create new draft
        Loader draft = draftService.createDraft(dto, adminUsername, ChangeType.ROLLBACK, null);

        log.info("Rollback draft created: loader_code={}, new_version={}, restored_from_version={}",
                loaderCode, draft.getVersionNumber(), versionNumber);

        return draft;
    }
}
```

---

## Summary

**Benefits of Modular Approach:**
1. ✅ Each service has **one clear responsibility**
2. ✅ **Easy to test** - mock dependencies
3. ✅ **Easy to understand** - small, focused code
4. ✅ **Easy to maintain** - changes isolated
5. ✅ **Reusable** - services compose cleanly
6. ✅ **No circular dependencies** - clean dependency graph

**Service Sizes:**
- LoaderDraftService: ~150 lines
- LoaderArchiveService: ~60 lines
- LoaderApprovalService: ~50 lines
- LoaderRejectionService: ~45 lines
- LoaderRevocationService: ~40 lines
- LoaderVersionHistoryService: ~50 lines
- LoaderRollbackService: ~40 lines

**Total:** ~435 lines across 7 services vs ~600 lines in 2 monolithic services

---

**Next:** Review and approve this modular design, then proceed with implementation.
