package com.tiqmo.monitoring.loader.service.versioning;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderArchive;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderArchiveRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.workflow.service.AbstractRejectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rejection service for Loader entities.
 * Handles rejecting PENDING_APPROVAL drafts with mandatory rejection reason.
 *
 * <p><b>Workflow:</b>
 * <ol>
 *   <li>Validates draft is PENDING_APPROVAL</li>
 *   <li>Sets rejection metadata (rejected_by, rejected_at, rejection_reason)</li>
 *   <li>Archives rejected draft for audit trail</li>
 *   <li>Deletes draft from main table</li>
 * </ol>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * loaderRejectionService.rejectDraft(draftId, "admin", "SQL query contains security vulnerability");
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoaderRejectionService extends AbstractRejectionService<Loader> {

    private final LoaderRepository loaderRepository;
    private final LoaderArchiveService loaderArchiveService;
    private final LoaderArchiveRepository loaderArchiveRepository;

    @Override
    protected Loader findById(Long id) {
        return loaderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Loader not found: id=" + id));
    }

    @Override
    protected Loader save(Loader entity) {
        return loaderRepository.save(entity);
    }

    @Override
    protected void deleteEntity(Loader entity) {
        loaderRepository.delete(entity);
    }

    @Override
    protected void archiveEntity(Loader entity, String archivedBy, String reason) {
        loaderArchiveService.archiveEntity(entity, archivedBy, reason);
    }

    @Override
    protected long countRejectedDraftsByEntityCode(String entityCode) {
        return loaderArchiveRepository.countByLoaderCodeAndRejectedByIsNotNull(entityCode);
    }

    @Override
    protected List<Loader> findRejectedDraftsByEntityCode(String entityCode) {
        // Return rejected drafts from archive
        List<LoaderArchive> rejectedArchives = loaderArchiveRepository.findRejectedByLoaderCode(entityCode);

        // Convert LoaderArchive to Loader for compatibility with abstract service
        return rejectedArchives.stream()
                .map(this::convertArchiveToLoader)
                .toList();
    }

    /**
     * Convert LoaderArchive to Loader for compatibility with abstract service.
     * Creates a Loader entity with all fields populated from archive.
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
}
