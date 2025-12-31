package com.tiqmo.monitoring.workflow.service;

import com.tiqmo.monitoring.workflow.domain.ChangeType;
import com.tiqmo.monitoring.workflow.domain.VersionStatus;
import com.tiqmo.monitoring.workflow.domain.WorkflowEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Abstract base class for draft management.
 *
 * <p>Entity-specific services extend this and provide:
 * <ul>
 *   <li>Repository operations (find, save, delete)</li>
 *   <li>Entity-specific field updates</li>
 * </ul>
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
public abstract class AbstractDraftService<T extends WorkflowEntity> {

    /**
     * Create a new draft.
     *
     * <p><b>Logic:</b>
     * <ul>
     *   <li>If ACTIVE exists + DRAFT exists → Replace draft (cumulative)</li>
     *   <li>If ACTIVE exists + no DRAFT → Create new version draft</li>
     *   <li>If no ACTIVE + no DRAFT → Create version 1</li>
     * </ul>
     *
     * @param draftData Entity data for the draft
     * @param username User creating the draft
     * @param changeType Source of change
     * @param importLabel Optional import batch identifier
     * @return Created or updated draft
     */
    @Transactional
    public T createDraft(T draftData, String username, ChangeType changeType, String importLabel) {
        String entityCode = draftData.getEntityCode();
        log.info("Creating draft: entity_code={}, user={}, changeType={}",
                entityCode, username, changeType);

        // Check for existing versions
        T activeVersion = findActiveByEntityCode(entityCode);
        T existingDraft = findDraftByEntityCode(entityCode);

        T draft;

        if (existingDraft != null) {
            // Replace existing draft (cumulative drafts)
            log.info("Replacing existing draft ID {} for entity_code: {}",
                    existingDraft.getId(), entityCode);
            draft = existingDraft;
            updateEntityFields(draft, draftData);
            draft.setModifiedBy(username);
            draft.setModifiedAt(Instant.now());
        } else {
            // Create new draft
            draft = createNewEntity();
            copyEntityIdentity(draft, draftData);
            draft.setVersionStatus(VersionStatus.DRAFT);
            draft.setCreatedBy(username);
            draft.setCreatedAt(Instant.now());
            draft.setChangeType(changeType);
            draft.setImportLabel(importLabel);
            draft.setParentVersionId(activeVersion != null ? activeVersion.getId() : null);
            updateEntityFields(draft, draftData);
        }

        draft.setChangeSummary(draftData.getChangeSummary() != null ? draftData.getChangeSummary() :
                activeVersion == null ? "New entity" : "Updated entity");

        draft = save(draft);
        log.info("Draft created: entity_code={}, version={}", draft.getEntityCode(), draft.getVersionNumber());

        return draft;
    }

    /**
     * Update existing draft.
     *
     * @param draftId Draft entity ID
     * @param draftData Updated entity data
     * @param username User making the update
     * @return Updated draft
     */
    @Transactional
    public T updateDraft(Long draftId, T draftData, String username) {
        T draft = findById(draftId);

        if (!draft.getVersionStatus().isDraft()) {
            throw new IllegalStateException("Cannot update non-draft entity. Status: " + draft.getVersionStatus());
        }

        log.info("Updating draft: entity_code={}, version={}", draft.getEntityCode(), draft.getVersionNumber());

        updateEntityFields(draft, draftData);
        draft.setModifiedBy(username);
        draft.setModifiedAt(Instant.now());
        draft.setChangeSummary(draftData.getChangeSummary());

        draft = save(draft);
        log.info("Draft updated: entity_code={}, version={}", draft.getEntityCode(), draft.getVersionNumber());

        return draft;
    }

    /**
     * Submit draft for approval (DRAFT → PENDING_APPROVAL).
     *
     * @param draftId Draft entity ID
     * @param username User submitting for approval
     * @return Updated draft with PENDING_APPROVAL status
     */
    @Transactional
    public T submitForApproval(Long draftId, String username) {
        T draft = findById(draftId);

        if (draft.getVersionStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT entities can be submitted. Status: " + draft.getVersionStatus());
        }

        log.info("Submitting draft for approval: entity_code={}, version={}",
                draft.getEntityCode(), draft.getVersionNumber());

        draft.setVersionStatus(VersionStatus.PENDING_APPROVAL);
        draft.setModifiedBy(username);
        draft.setModifiedAt(Instant.now());

        draft = save(draft);
        log.info("Draft submitted for approval: entity_code={}, version={}",
                draft.getEntityCode(), draft.getVersionNumber());

        return draft;
    }

    /**
     * Delete draft (only DRAFT status, not PENDING).
     *
     * @param draftId Draft entity ID
     * @param username User deleting the draft
     */
    @Transactional
    public void deleteDraft(Long draftId, String username) {
        T draft = findById(draftId);

        if (draft.getVersionStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT entities can be deleted. Status: " + draft.getVersionStatus());
        }

        log.info("Deleting draft: entity_code={}, version={} by user: {}",
                draft.getEntityCode(), draft.getVersionNumber(), username);

        delete(draft);
    }

    /**
     * Get draft by entity code.
     *
     * @param entityCode Entity code
     * @return Draft entity or null if not found
     */
    @Transactional(readOnly = true)
    public T getDraft(String entityCode) {
        return findDraftByEntityCode(entityCode);
    }

    // ==================== ABSTRACT METHODS (Entity-Specific) ====================

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
     * Create new entity instance.
     * Example: return new Loader();
     *
     * @return New entity instance
     */
    protected abstract T createNewEntity();

    /**
     * Copy entity identity from source to target.
     * Example: target.setLoaderCode(source.getLoaderCode());
     *
     * @param target Target entity
     * @param source Source entity
     */
    protected abstract void copyEntityIdentity(T target, T source);

    /**
     * Update entity-specific fields from source to target.
     * Example: target.setLoaderSql(source.getLoaderSql());
     *
     * @param target Target entity
     * @param source Source entity
     */
    protected abstract void updateEntityFields(T target, T source);

    /**
     * Save entity to repository.
     * Example: return loaderRepository.save(entity);
     *
     * @param entity Entity to save
     * @return Saved entity
     */
    protected abstract T save(T entity);

    /**
     * Delete entity from repository.
     * Example: loaderRepository.delete(entity);
     *
     * @param entity Entity to delete
     */
    protected abstract void delete(T entity);

    /**
     * Find entity by ID.
     * Example: return loaderRepository.findById(id).orElseThrow(...);
     *
     * @param id Entity ID
     * @return Entity
     * @throws IllegalArgumentException if not found
     */
    protected abstract T findById(Long id);
}
