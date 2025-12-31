package com.tiqmo.monitoring.loader.service.versioning;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderArchive;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderArchiveRepository;
import com.tiqmo.monitoring.workflow.service.AbstractArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Archive service for Loader entities.
 * Handles moving loader versions to archive table for audit trail and rollback support.
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * loaderArchiveService.archiveEntity(oldActiveLoader, "admin", "Replaced by version 3");
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoaderArchiveService extends AbstractArchiveService<Loader> {

    private final LoaderArchiveRepository loaderArchiveRepository;

    @Override
    protected Long saveToArchive(Loader entity, String archivedBy, String reason, Instant archivedAt) {
        LoaderArchive archive = LoaderArchive.fromLoader(entity, archivedBy, reason, archivedAt);
        archive = loaderArchiveRepository.save(archive);

        log.info("Loader archived: loader_code={}, version={}, archive_id={}",
                entity.getLoaderCode(), entity.getVersionNumber(), archive.getId());

        return archive.getId();
    }

    @Override
    protected boolean existsInArchive(String entityCode, Integer versionNumber) {
        return loaderArchiveRepository.existsByLoaderCodeAndVersionNumber(entityCode, versionNumber);
    }

    @Override
    protected Long getArchiveIdByEntityCodeAndVersion(String entityCode, Integer versionNumber) {
        return loaderArchiveRepository.findByLoaderCodeAndVersionNumber(entityCode, versionNumber)
                .map(LoaderArchive::getId)
                .orElse(null);
    }

    @Override
    protected List<Loader> findArchivedVersionsByEntityCode(String entityCode) {
        // Note: This returns LoaderArchive entities cast to Loader
        // For proper type safety, consider returning List<LoaderArchive> instead
        return loaderArchiveRepository.findByLoaderCodeOrderByVersionNumberDesc(entityCode)
                .stream()
                .map(this::convertArchiveToLoader)
                .toList();
    }

    @Override
    protected Loader findArchivedVersionByEntityCodeAndVersion(String entityCode, Integer versionNumber) {
        return loaderArchiveRepository.findByLoaderCodeAndVersionNumber(entityCode, versionNumber)
                .map(this::convertArchiveToLoader)
                .orElse(null);
    }

    /**
     * Public method to get archived versions for a loader.
     * Returns all archived versions sorted by version number descending.
     *
     * @param loaderCode Loader code
     * @return List of archived loader versions
     */
    public List<Loader> getArchivedVersions(String loaderCode) {
        return findArchivedVersionsByEntityCode(loaderCode);
    }

    /**
     * Public method to get a specific archived version.
     *
     * @param loaderCode Loader code
     * @param versionNumber Version number
     * @return Loader version or null if not found
     */
    public Loader getArchivedVersion(String loaderCode, Integer versionNumber) {
        return findArchivedVersionByEntityCodeAndVersion(loaderCode, versionNumber);
    }

    @Override
    protected long countByEntityCode(String entityCode) {
        return loaderArchiveRepository.countByLoaderCode(entityCode);
    }

    /**
     * Convert LoaderArchive to Loader for compatibility with abstract service.
     * Creates a Loader entity with all fields populated from archive.
     *
     * <p><b>Note:</b> SourceDatabase relationship is not preserved (only ID is stored in archive).
     *
     * @param archive LoaderArchive entity
     * @return Loader entity with populated fields
     */
    private Loader convertArchiveToLoader(LoaderArchive archive) {
        return Loader.builder()
                .id(archive.getOriginalLoaderId())
                .loaderCode(archive.getLoaderCode())
                .loaderSql(archive.getLoaderSql())
                .minIntervalSeconds(archive.getMinIntervalSeconds())
                .maxIntervalSeconds(archive.getMaxIntervalSeconds())
                .maxQueryPeriodSeconds(archive.getMaxQueryPeriodSeconds())
                .maxParallelExecutions(archive.getMaxParallelExecutions())
                .sourceTimezoneOffsetHours(archive.getSourceTimezoneOffsetHours())
                .lastLoadTimestamp(archive.getLastLoadTimestamp())
                .failedSince(archive.getFailedSince())
                .consecutiveZeroRecordRuns(archive.getConsecutiveZeroRecordRuns())
                .loadStatus(archive.getLoadStatus())
                .purgeStrategy(archive.getPurgeStrategy())
                .enabled(archive.isEnabled())
                .aggregationPeriodSeconds(archive.getAggregationPeriodSeconds())
                .createdAt(archive.getCreatedAt())
                .updatedAt(archive.getUpdatedAt())
                .versionStatus(archive.getVersionStatus())
                .versionNumber(archive.getVersionNumber())
                .parentVersionId(archive.getParentVersionId())
                .createdBy(archive.getCreatedBy())
                .modifiedBy(archive.getModifiedBy())
                .modifiedAt(archive.getModifiedAt())
                .approvedByVersion(archive.getApprovedByVersion())
                .approvedAtVersion(archive.getApprovedAtVersion())
                .rejectedBy(archive.getRejectedBy())
                .rejectedAt(archive.getRejectedAt())
                .rejectionReason(archive.getRejectionReason())
                .changeSummary(archive.getChangeSummary())
                .changeType(archive.getChangeType())
                .importLabel(archive.getImportLabel())
                .build();
    }

    /**
     * Get rejection history for a loader (archive records with rejected_by set).
     *
     * @param loaderCode Loader code
     * @return List of rejected loader versions
     */
    public List<LoaderArchive> getRejectionHistory(String loaderCode) {
        return loaderArchiveRepository.findRejectedByLoaderCode(loaderCode);
    }
}
