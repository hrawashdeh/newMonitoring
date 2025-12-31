package com.tiqmo.monitoring.loader.service.versioning;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.workflow.service.AbstractDraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Draft management service for Loader entities.
 * Handles creating, updating, and submitting loader drafts for approval.
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * Loader draft = loaderDraftService.createDraft(loaderData, "operator", ChangeType.MANUAL_EDIT, null);
 * loaderDraftService.submitForApproval(draft.getId(), "operator");
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoaderDraftService extends AbstractDraftService<Loader> {

    private final LoaderRepository loaderRepository;

    @Override
    protected Loader findActiveByEntityCode(String entityCode) {
        return loaderRepository.findActiveByLoaderCode(entityCode).orElse(null);
    }

    @Override
    protected Loader findDraftByEntityCode(String entityCode) {
        return loaderRepository.findDraftByLoaderCode(entityCode).orElse(null);
    }

    @Override
    protected Loader createNewEntity() {
        return new Loader();
    }

    @Override
    protected void copyEntityIdentity(Loader target, Loader source) {
        // Copy immutable business key
        target.setLoaderCode(source.getLoaderCode());
    }

    @Override
    protected void updateEntityFields(Loader target, Loader source) {
        // Update loader configuration fields
        target.setLoaderSql(source.getLoaderSql());
        target.setMinIntervalSeconds(source.getMinIntervalSeconds());
        target.setMaxIntervalSeconds(source.getMaxIntervalSeconds());
        target.setMaxQueryPeriodSeconds(source.getMaxQueryPeriodSeconds());
        target.setMaxParallelExecutions(source.getMaxParallelExecutions());
        target.setSourceTimezoneOffsetHours(source.getSourceTimezoneOffsetHours());
        target.setEnabled(source.isEnabled());
        target.setAggregationPeriodSeconds(source.getAggregationPeriodSeconds());
        target.setPurgeStrategy(source.getPurgeStrategy());

        // Update source database reference
        if (source.getSourceDatabase() != null) {
            target.setSourceDatabase(source.getSourceDatabase());
        }

        // Note: Runtime state fields (lastLoadTimestamp, failedSince, etc.) are NOT copied
        // They are specific to the active version and should not be overwritten by drafts
    }

    @Override
    protected Loader save(Loader entity) {
        return loaderRepository.save(entity);
    }

    @Override
    protected void delete(Loader entity) {
        loaderRepository.delete(entity);
    }

    @Override
    protected Loader findById(Long id) {
        return loaderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Loader not found: id=" + id));
    }
}
