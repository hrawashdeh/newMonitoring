package com.tiqmo.monitoring.workflow.service;

import com.tiqmo.monitoring.workflow.domain.WorkflowEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Abstract base class for archival operations.
 *
 * <p>Handles moving entities to archive tables when:
 * <ul>
 *   <li>ACTIVE version is replaced by newly approved version</li>
 *   <li>Draft is rejected (PENDING → ARCHIVED)</li>
 *   <li>Active entity is revoked by admin (ACTIVE → ARCHIVED)</li>
 * </ul>
 *
 * <p><b>Template Method Pattern:</b>
 * This class defines the workflow skeleton, concrete classes fill in entity-specific details.
 *
 * <p><b>Note:</b> Archive tables are entity-specific (loader_archive, dashboard_archive, etc.)
 * and must be created via database migrations.
 *
 * @param <T> Entity type (Loader, Dashboard, Incident)
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-30
 */
@Slf4j
public abstract class AbstractArchiveService<T extends WorkflowEntity> {

    /**
     * Archive an entity to the archive table.
     *
     * <p><b>Workflow:</b>
     * <ol>
     *   <li>Validate entity exists</li>
     *   <li>Create archive record with metadata</li>
     *   <li>Save to entity-specific archive table</li>
     * </ol>
     *
     * <p><b>Important:</b> This does NOT delete the entity from the main table.
     * Caller must delete the entity separately if needed.
     *
     * @param entity Entity to archive
     * @param archivedBy Username archiving the entity
     * @param reason Reason for archival (e.g., "Replaced by version 3", "Rejected by admin", "Revoked due to errors")
     * @return Archive record ID
     */
    @Transactional
    public Long archiveEntity(T entity, String archivedBy, String reason) {
        if (entity == null) {
            throw new IllegalArgumentException("Cannot archive null entity");
        }

        String entityCode = entity.getEntityCode();
        Integer versionNumber = entity.getVersionNumber();

        log.info("Archiving entity: entity_code={}, version={}, archived_by={}, reason={}",
                entityCode, versionNumber, archivedBy, reason);

        // Check if already archived
        if (existsInArchive(entityCode, versionNumber)) {
            log.warn("Entity already archived: entity_code={}, version={}", entityCode, versionNumber);
            return getArchiveIdByEntityCodeAndVersion(entityCode, versionNumber);
        }

        // Save to archive table (entity-specific implementation)
        Long archiveId = saveToArchive(entity, archivedBy, reason, Instant.now());

        log.info("Entity archived successfully: entity_code={}, version={}, archive_id={}",
                entityCode, versionNumber, archiveId);

        return archiveId;
    }

    /**
     * Get version history for an entity.
     * Returns all archived versions sorted by version number descending.
     *
     * @param entityCode Entity code
     * @return List of archived versions (newest first)
     */
    @Transactional(readOnly = true)
    public List<T> getVersionHistory(String entityCode) {
        log.debug("Fetching version history for entity_code={}", entityCode);
        return findArchivedVersionsByEntityCode(entityCode);
    }

    /**
     * Get specific archived version.
     *
     * @param entityCode Entity code
     * @param versionNumber Version number
     * @return Archived entity or null if not found
     */
    @Transactional(readOnly = true)
    public T getArchivedVersion(String entityCode, Integer versionNumber) {
        log.debug("Fetching archived version: entity_code={}, version={}", entityCode, versionNumber);
        return findArchivedVersionByEntityCodeAndVersion(entityCode, versionNumber);
    }

    /**
     * Count archived versions for an entity.
     *
     * @param entityCode Entity code
     * @return Number of archived versions
     */
    @Transactional(readOnly = true)
    public long countArchivedVersions(String entityCode) {
        return countByEntityCode(entityCode);
    }

    /**
     * Check if entity version exists in archive.
     *
     * @param entityCode Entity code
     * @param versionNumber Version number
     * @return true if archived version exists
     */
    @Transactional(readOnly = true)
    public boolean isVersionArchived(String entityCode, Integer versionNumber) {
        return existsInArchive(entityCode, versionNumber);
    }

    /**
     * Get latest archived version for an entity.
     * Useful for rollback operations.
     *
     * @param entityCode Entity code
     * @return Latest archived version or null if no archives exist
     */
    @Transactional(readOnly = true)
    public T getLatestArchivedVersion(String entityCode) {
        log.debug("Fetching latest archived version for entity_code={}", entityCode);
        List<T> history = findArchivedVersionsByEntityCode(entityCode);
        return history.isEmpty() ? null : history.get(0);
    }

    // ==================== ABSTRACT METHODS (Entity-Specific) ====================

    /**
     * Save entity to archive table.
     * Entity-specific implementation must:
     * <ul>
     *   <li>Copy all entity fields to archive entity</li>
     *   <li>Set archive metadata (archived_at, archived_by, archive_reason)</li>
     *   <li>Save to archive repository</li>
     *   <li>Return archive record ID</li>
     * </ul>
     *
     * Example:
     * <pre>
     * LoaderArchive archive = LoaderArchive.fromLoader(entity, archivedBy, reason, archivedAt);
     * archive = loaderArchiveRepository.save(archive);
     * return archive.getId();
     * </pre>
     *
     * @param entity Entity to archive
     * @param archivedBy Username archiving the entity
     * @param reason Reason for archival
     * @param archivedAt Archive timestamp
     * @return Archive record ID
     */
    protected abstract Long saveToArchive(T entity, String archivedBy, String reason, Instant archivedAt);

    /**
     * Check if entity version exists in archive table.
     * Example: return loaderArchiveRepository.existsByLoaderCodeAndVersionNumber(entityCode, versionNumber)
     *
     * @param entityCode Entity code
     * @param versionNumber Version number
     * @return true if archived version exists
     */
    protected abstract boolean existsInArchive(String entityCode, Integer versionNumber);

    /**
     * Get archive ID by entity code and version number.
     * Used to return existing archive ID if entity is already archived.
     * Example: return loaderArchiveRepository.findByLoaderCodeAndVersionNumber(entityCode, versionNumber).map(LoaderArchive::getId).orElse(null)
     *
     * @param entityCode Entity code
     * @param versionNumber Version number
     * @return Archive record ID or null if not found
     */
    protected abstract Long getArchiveIdByEntityCodeAndVersion(String entityCode, Integer versionNumber);

    /**
     * Find all archived versions by entity code.
     * Must return results ordered by version_number DESC (newest first).
     * Example: return loaderArchiveRepository.findByLoaderCodeOrderByVersionNumberDesc(entityCode)
     *
     * @param entityCode Entity code
     * @return List of archived entities (newest first)
     */
    protected abstract List<T> findArchivedVersionsByEntityCode(String entityCode);

    /**
     * Find specific archived version by entity code and version number.
     * Example: return loaderArchiveRepository.findByLoaderCodeAndVersionNumber(entityCode, versionNumber).orElse(null)
     *
     * @param entityCode Entity code
     * @param versionNumber Version number
     * @return Archived entity or null if not found
     */
    protected abstract T findArchivedVersionByEntityCodeAndVersion(String entityCode, Integer versionNumber);

    /**
     * Count archived versions for entity code.
     * Example: return loaderArchiveRepository.countByLoaderCode(entityCode)
     *
     * @param entityCode Entity code
     * @return Number of archived versions
     */
    protected abstract long countByEntityCode(String entityCode);
}
