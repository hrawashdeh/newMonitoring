package com.tiqmo.monitoring.loader.service.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiqmo.monitoring.loader.domain.approval.entity.ApprovalRequest;
import com.tiqmo.monitoring.loader.domain.approval.repo.ApprovalRequestRepository;
import com.tiqmo.monitoring.loader.domain.loader.entity.ApprovalStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.PurgeStrategy;
import com.tiqmo.monitoring.loader.domain.loader.entity.SourceDatabase;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.SourceDatabaseRepository;
import com.tiqmo.monitoring.loader.dto.loader.EtlLoaderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Approval Request Processor
 *
 * <p>Scheduled service that processes APPROVED approval requests by creating
 * the actual entities (Loader, Dashboard, etc.) from the approved request data.
 *
 * <p>This processor bridges the gap between the approval workflow and the actual
 * entity creation. When an admin approves a request, the approval status changes
 * to APPROVED, but the actual loader hasn't been created yet. This processor
 * creates it.
 *
 * <p><b>RE-ENABLED (2025-12-31):</b> This class was disabled when V17 migration
 * was implemented, but V17 has been removed and V18 restored the approval_request
 * table. This processor is now active again to process approved requests.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.1
 * @since 2025-12-30
 */
@Service  // RE-ENABLED: approval_request table restored in V18 migration
@RequiredArgsConstructor
@Slf4j
public class ApprovalRequestProcessor {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final LoaderRepository loaderRepository;
    private final SourceDatabaseRepository sourceDbRepository;
    private final ObjectMapper objectMapper;

    /**
     * Process approved loader requests every 10 seconds
     *
     * <p>Finds all APPROVED loader requests and creates the actual Loader entities
     * from the JSON data stored in the request.
     */
    @Scheduled(fixedDelay = 10000) // Run every 10 seconds
    @Transactional
    public void processApprovedLoaderRequests() {
        try {
            // Find all APPROVED loader requests
            List<ApprovalRequest> approvedRequests = approvalRequestRepository
                    .findByEntityTypeAndApprovalStatusOrderByRequestedAtDesc(
                            ApprovalRequest.EntityType.LOADER,
                            ApprovalRequest.ApprovalStatus.APPROVED
                    );

            if (approvedRequests.isEmpty()) {
                return; // Nothing to process
            }

            log.info("Found {} APPROVED loader requests to process", approvedRequests.size());

            int processedCount = 0;
            int skippedCount = 0;
            int errorCount = 0;

            for (ApprovalRequest request : approvedRequests) {
                try {
                    // Check if loader already exists (to avoid duplicates on multiple runs)
                    if (loaderRepository.findByLoaderCode(request.getEntityId()).isPresent()) {
                        log.debug("Loader {} already exists, skipping approval request ID {}",
                                request.getEntityId(), request.getId());
                        skippedCount++;
                        continue;
                    }

                    // Deserialize the request data to EtlLoaderDto
                    EtlLoaderDto dto = objectMapper.readValue(
                            request.getRequestData(),
                            EtlLoaderDto.class
                    );

                    // Create the actual Loader entity
                    Loader loader = createLoaderFromDto(dto, request);

                    // Save to database
                    loaderRepository.save(loader);

                    log.info("Created loader {} from approved request ID {} (requested by: {})",
                            loader.getLoaderCode(), request.getId(), request.getRequestedBy());

                    processedCount++;

                } catch (Exception e) {
                    log.error("Failed to process approval request ID {} for loader {}: {}",
                            request.getId(), request.getEntityId(), e.getMessage(), e);
                    errorCount++;
                }
            }

            if (processedCount > 0 || errorCount > 0) {
                log.info("Approval request processing completed: processed={}, skipped={}, errors={}",
                        processedCount, skippedCount, errorCount);
            }

        } catch (Exception e) {
            log.error("Error in approval request processor: {}", e.getMessage(), e);
        }
    }

    /**
     * Create a Loader entity from EtlLoaderDto
     *
     * @param dto Loader DTO from approval request JSON
     * @param request The approval request
     * @return Loader entity ready to be saved
     */
    private Loader createLoaderFromDto(EtlLoaderDto dto, ApprovalRequest request) {
        // Find source database
        SourceDatabase sourceDb = sourceDbRepository.findById(dto.getSourceDatabaseId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source database not found with ID: " + dto.getSourceDatabaseId()
                ));

        // Create Loader entity
        Loader loader = new Loader();
        loader.setLoaderCode(dto.getLoaderCode());
        loader.setLoaderSql(dto.getLoaderSql());
        loader.setSourceDatabase(sourceDb);
        loader.setMinIntervalSeconds(dto.getMinIntervalSeconds() != null ? dto.getMinIntervalSeconds() : 10);
        loader.setMaxIntervalSeconds(dto.getMaxIntervalSeconds() != null ? dto.getMaxIntervalSeconds() : 60);
        loader.setMaxQueryPeriodSeconds(dto.getMaxQueryPeriodSeconds() != null ? dto.getMaxQueryPeriodSeconds() : 432000);
        loader.setMaxParallelExecutions(dto.getMaxParallelExecutions() != null ? dto.getMaxParallelExecutions() : 1);
        // loadStatus defaults to IDLE in Loader entity
        loader.setConsecutiveZeroRecordRuns(0);
        loader.setPurgeStrategy(dto.getPurgeStrategy() != null ?
                PurgeStrategy.valueOf(dto.getPurgeStrategy().toUpperCase()) :
                PurgeStrategy.FAIL_ON_DUPLICATE);
        loader.setAggregationPeriodSeconds(dto.getAggregationPeriodSeconds());
        loader.setSourceTimezoneOffsetHours(dto.getSourceTimezoneOffsetHours() != null ?
                dto.getSourceTimezoneOffsetHours() : 0);

        // Set approval status to APPROVED (entity was created from an approved request)
        loader.setApprovalStatus(ApprovalStatus.APPROVED);
        loader.setApprovedBy(request.getApprovedBy());
        loader.setApprovedAt(request.getApprovedAt() != null ?
                request.getApprovedAt().toInstant(java.time.ZoneOffset.UTC) :
                Instant.now());

        // Set enabled based on DTO, but default to false for safety
        // Note: Database constraint requires APPROVED status before enabled=true
        loader.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : false);

        return loader;
    }
}
