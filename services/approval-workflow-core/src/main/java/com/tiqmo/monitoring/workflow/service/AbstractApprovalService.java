package com.tiqmo.monitoring.workflow.service;

import com.tiqmo.monitoring.workflow.domain.VersionStatus;
import com.tiqmo.monitoring.workflow.domain.WorkflowEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Abstract base class for approval management.
 *
 * <p>Handles the approval workflow:
 * <ol>
 *   <li>Validate draft is PENDING_APPROVAL</li>
 *   <li>Archive existing ACTIVE version (if exists)</li>
 *   <li>Promote PENDING draft to ACTIVE</li>
 *   <li>Set approval metadata</li>
 * </ol>
 *
 * <p><b>Template Method Pattern:</b>
 * This class defines the workflow skeleton, concrete classes fill in entity-specific details.
 *
 * @param <T> Entity type (Loader, Dashboard, Incident)
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-30
 */
@Slf4j
public abstract class AbstractApprovalService<T extends WorkflowEntity> {

    /**
     * Approve a pending draft and promote it to ACTIVE.
     *
     * <p><b>Workflow:</b>
     * <ol>
     *   <li>Find draft by ID</li>
     *   <li>Validate status is PENDING_APPROVAL</li>
     *   <li>Find existing ACTIVE version</li>
     *   <li>Archive the ACTIVE version (if exists)</li>
     *   <li>Update draft to ACTIVE status</li>
     *   <li>Set approval metadata</li>
     *   <li>Save the now-ACTIVE entity</li>
     * </ol>
     *
     * @param draftId Draft entity ID
     * @param adminUsername Admin approving the draft
     * @param approvalComments Optional comments from admin
     * @return Newly activated entity
     * @throws IllegalStateException if draft is not PENDING_APPROVAL
     * @throws IllegalArgumentException if draft not found
     */
    @Transactional
    public T approveDraft(Long draftId, String adminUsername, String approvalComments) {
        T draft = findById(draftId);
        String entityCode = draft.getEntityCode();

        // Validate status
        if (draft.getVersionStatus() != VersionStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    String.format("Cannot approve non-PENDING entity. Status: %s, entity_code: %s",
                            draft.getVersionStatus(), entityCode)
            );
        }

        log.info("Approving draft: entity_code={}, version={}, admin={}",
                entityCode, draft.getVersionNumber(), adminUsername);

        // Find existing ACTIVE version
        T activeVersion = findActiveByEntityCode(entityCode);

        // Archive old ACTIVE version if exists
        if (activeVersion != null) {
            log.info("Archiving old ACTIVE version: entity_code={}, version={}",
                    entityCode, activeVersion.getVersionNumber());
            archiveEntity(activeVersion, adminUsername, "Replaced by new approved version");
            deleteEntity(activeVersion);
        }

        // Promote draft to ACTIVE
        draft.setVersionStatus(VersionStatus.ACTIVE);
        draft.setApprovedByVersion(adminUsername);
        draft.setApprovedAtVersion(Instant.now());
        draft.setModifiedBy(adminUsername);
        draft.setModifiedAt(Instant.now());

        // Append approval comments to change summary if provided
        if (approvalComments != null && !approvalComments.isBlank()) {
            String existingSummary = draft.getChangeSummary() != null ? draft.getChangeSummary() : "";
            draft.setChangeSummary(existingSummary + "\n[Approval Comments] " + approvalComments);
        }

        draft = save(draft);

        log.info("Draft approved and activated: entity_code={}, version={}, approved_by={}",
                draft.getEntityCode(), draft.getVersionNumber(), adminUsername);

        return draft;
    }

    /**
     * Approve draft without additional comments.
     *
     * @param draftId Draft entity ID
     * @param adminUsername Admin approving the draft
     * @return Newly activated entity
     */
    @Transactional
    public T approveDraft(Long draftId, String adminUsername) {
        return approveDraft(draftId, adminUsername, null);
    }

    /**
     * Check if entity has pending approval.
     *
     * @param entityCode Entity code
     * @return true if there's a PENDING_APPROVAL draft
     */
    @Transactional(readOnly = true)
    public boolean hasPendingApproval(String entityCode) {
        T draft = findDraftByEntityCode(entityCode);
        return draft != null && draft.getVersionStatus() == VersionStatus.PENDING_APPROVAL;
    }

    /**
     * Get pending draft for approval.
     *
     * @param entityCode Entity code
     * @return PENDING_APPROVAL draft or null if not found
     */
    @Transactional(readOnly = true)
    public T getPendingDraft(String entityCode) {
        T draft = findDraftByEntityCode(entityCode);
        if (draft != null && draft.getVersionStatus() != VersionStatus.PENDING_APPROVAL) {
            return null; // Not pending, return null
        }
        return draft;
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
     * Find ACTIVE version by entity code.
     * Example: loaderRepository.findActiveByLoaderCode(code)
     *
     * @param entityCode Entity code
     * @return ACTIVE entity or null
     */
    protected abstract T findActiveByEntityCode(String entityCode);

    /**
     * Find DRAFT or PENDING_APPROVAL version by entity code.
     * Example: loaderRepository.findDraftByLoaderCode(code)
     *
     * @param entityCode Entity code
     * @return DRAFT/PENDING entity or null
     */
    protected abstract T findDraftByEntityCode(String entityCode);

    /**
     * Save entity to repository.
     * Example: return loaderRepository.save(entity)
     *
     * @param entity Entity to save
     * @return Saved entity
     */
    protected abstract T save(T entity);

    /**
     * Delete entity from repository.
     * Used to delete old ACTIVE version after archiving.
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
}
