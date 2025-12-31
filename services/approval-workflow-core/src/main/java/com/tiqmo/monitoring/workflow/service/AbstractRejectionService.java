package com.tiqmo.monitoring.workflow.service;

import com.tiqmo.monitoring.workflow.domain.VersionStatus;
import com.tiqmo.monitoring.workflow.domain.WorkflowEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Abstract base class for rejection management.
 *
 * <p>Handles the rejection workflow:
 * <ol>
 *   <li>Validate draft is PENDING_APPROVAL</li>
 *   <li>Set rejection metadata</li>
 *   <li>Archive rejected draft</li>
 *   <li>Delete draft from main table</li>
 * </ol>
 *
 * <p><b>Template Method Pattern:</b>
 * This class defines the workflow skeleton, concrete classes fill in entity-specific details.
 *
 * <p><b>Note:</b> Rejected drafts are archived for audit purposes and cannot be resubmitted.
 * Users must create a new draft to resubmit changes.
 *
 * @param <T> Entity type (Loader, Dashboard, Incident)
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-30
 */
@Slf4j
public abstract class AbstractRejectionService<T extends WorkflowEntity> {

    /**
     * Reject a pending draft.
     *
     * <p><b>Workflow:</b>
     * <ol>
     *   <li>Find draft by ID</li>
     *   <li>Validate status is PENDING_APPROVAL</li>
     *   <li>Set rejection metadata</li>
     *   <li>Save rejection metadata to entity</li>
     *   <li>Archive the rejected draft</li>
     *   <li>Delete draft from main table</li>
     * </ol>
     *
     * <p><b>Important:</b> Rejection reason is mandatory and will be visible to the entity creator.
     * Provide clear, actionable feedback about what needs to be fixed.
     *
     * @param draftId Draft entity ID
     * @param adminUsername Admin rejecting the draft
     * @param rejectionReason Mandatory reason for rejection (user feedback)
     * @throws IllegalStateException if draft is not PENDING_APPROVAL
     * @throws IllegalArgumentException if draft not found or rejection reason is blank
     */
    @Transactional
    public void rejectDraft(Long draftId, String adminUsername, String rejectionReason) {
        // Validate rejection reason
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is mandatory");
        }

        T draft = findById(draftId);
        String entityCode = draft.getEntityCode();
        Integer versionNumber = draft.getVersionNumber();

        // Validate status
        if (draft.getVersionStatus() != VersionStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    String.format("Cannot reject non-PENDING entity. Status: %s, entity_code: %s",
                            draft.getVersionStatus(), entityCode)
            );
        }

        log.info("Rejecting draft: entity_code={}, version={}, admin={}, reason={}",
                entityCode, versionNumber, adminUsername, rejectionReason);

        // Set rejection metadata
        draft.setRejectedBy(adminUsername);
        draft.setRejectedAt(Instant.now());
        draft.setRejectionReason(rejectionReason);
        draft.setModifiedBy(adminUsername);
        draft.setModifiedAt(Instant.now());

        // Save rejection metadata before archiving
        draft = save(draft);

        // Archive rejected draft (for audit trail)
        String archiveReason = String.format("Rejected by %s: %s", adminUsername, rejectionReason);
        archiveEntity(draft, adminUsername, archiveReason);

        // Delete from main table
        deleteEntity(draft);

        log.info("Draft rejected and archived: entity_code={}, version={}, rejected_by={}",
                entityCode, versionNumber, adminUsername);
    }

    /**
     * Check if entity code has any rejected drafts in archive.
     * Useful for audit trail and understanding rejection history.
     *
     * @param entityCode Entity code
     * @return true if there are rejected drafts in archive
     */
    @Transactional(readOnly = true)
    public boolean hasRejectedDrafts(String entityCode) {
        return countRejectedDraftsByEntityCode(entityCode) > 0;
    }

    /**
     * Get count of rejected drafts for an entity.
     *
     * @param entityCode Entity code
     * @return Number of rejected drafts
     */
    @Transactional(readOnly = true)
    public long getRejectedDraftCount(String entityCode) {
        return countRejectedDraftsByEntityCode(entityCode);
    }

    /**
     * Get rejection history for an entity.
     * Returns all rejected drafts from archive.
     *
     * @param entityCode Entity code
     * @return List of rejected drafts (newest first)
     */
    @Transactional(readOnly = true)
    public java.util.List<T> getRejectionHistory(String entityCode) {
        log.debug("Fetching rejection history for entity_code={}", entityCode);
        return findRejectedDraftsByEntityCode(entityCode);
    }

    // ==================== ABSTRACT METHODS (Entity-Specific) ====================

    /**
     * Find entity by ID.
     * Example: return loaderRepository.findById(id).orElseThrow(...)
     *
     * @param id Entity ID
     * @return Entity
     * @throws IllegalArgumentException if not found
     */
    protected abstract T findById(Long id);

    /**
     * Save entity to repository.
     * Used to persist rejection metadata before archiving.
     * Example: return loaderRepository.save(entity)
     *
     * @param entity Entity to save
     * @return Saved entity
     */
    protected abstract T save(T entity);

    /**
     * Delete entity from repository.
     * Used to remove rejected draft from main table after archiving.
     * Example: loaderRepository.delete(entity)
     *
     * @param entity Entity to delete
     */
    protected abstract void deleteEntity(T entity);

    /**
     * Archive entity to archive table.
     * Delegates to entity-specific archive service.
     * Example: loaderArchiveService.archiveEntity(entity, archivedBy, reason)
     *
     * @param entity Entity to archive
     * @param archivedBy Username archiving the entity
     * @param reason Reason for archival
     */
    protected abstract void archiveEntity(T entity, String archivedBy, String reason);

    /**
     * Count rejected drafts for entity code.
     * Queries archive table for entities with rejected_by not null.
     * Example: return loaderArchiveRepository.countByLoaderCodeAndRejectedByIsNotNull(entityCode)
     *
     * @param entityCode Entity code
     * @return Number of rejected drafts
     */
    protected abstract long countRejectedDraftsByEntityCode(String entityCode);

    /**
     * Find all rejected drafts for entity code from archive.
     * Must return results ordered by rejected_at DESC (newest first).
     * Example: return loaderArchiveRepository.findByLoaderCodeAndRejectedByIsNotNullOrderByRejectedAtDesc(entityCode)
     *
     * @param entityCode Entity code
     * @return List of rejected drafts (newest first)
     */
    protected abstract java.util.List<T> findRejectedDraftsByEntityCode(String entityCode);
}
