package com.tiqmo.monitoring.loader.service.versioning;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.workflow.service.AbstractApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Approval service for Loader entities.
 * Handles approving PENDING_APPROVAL drafts and promoting them to ACTIVE status.
 *
 * <p><b>Workflow:</b>
 * <ol>
 *   <li>Validates draft is PENDING_APPROVAL</li>
 *   <li>Archives existing ACTIVE version (if exists)</li>
 *   <li>Promotes draft to ACTIVE</li>
 *   <li>Sets approval metadata (approved_by, approved_at)</li>
 * </ol>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * Loader approvedLoader = loaderApprovalService.approveDraft(draftId, "admin", "Approved after security review");
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoaderApprovalService extends AbstractApprovalService<Loader> {

    private final LoaderRepository loaderRepository;
    private final LoaderArchiveService loaderArchiveService;

    @Override
    protected Loader findById(Long id) {
        return loaderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Loader not found: id=" + id));
    }

    @Override
    protected Loader findActiveByEntityCode(String entityCode) {
        return loaderRepository.findActiveByLoaderCode(entityCode).orElse(null);
    }

    @Override
    protected Loader findDraftByEntityCode(String entityCode) {
        return loaderRepository.findDraftByLoaderCode(entityCode).orElse(null);
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
}
