package com.tiqmo.monitoring.loader.service.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiqmo.monitoring.loader.domain.approval.entity.ApprovalAction;
import com.tiqmo.monitoring.loader.domain.approval.entity.ApprovalRequest;
import com.tiqmo.monitoring.loader.domain.approval.repo.ApprovalActionRepository;
import com.tiqmo.monitoring.loader.domain.approval.repo.ApprovalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Generic Approval Service
 *
 * Handles complete approval workflow for all entity types:
 * - Submit approval requests
 * - Approve/Reject with justifications
 * - Resubmit after rejection
 * - Revoke approvals
 * - Complete audit trail
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalActionRepository approvalActionRepository;
    private final ObjectMapper objectMapper;

    // ===== Submit Approval Request =====

    /**
     * Submit a new approval request
     *
     * @param entityType Entity type (LOADER, DASHBOARD, etc.)
     * @param entityId Entity identifier
     * @param requestType CREATE, UPDATE, DELETE
     * @param requestData Proposed new state (as object, will be serialized to JSON)
     * @param currentData Current state for UPDATE requests (as object, will be serialized to JSON)
     * @param changeSummary Human-readable summary of changes
     * @param requestedBy User submitting the request
     * @param source Source of request (WEB_UI, IMPORT, etc.)
     * @param importLabel Optional import batch label
     * @return Created approval request
     */
    @Transactional
    public ApprovalRequest submitApprovalRequest(
            ApprovalRequest.EntityType entityType,
            String entityId,
            ApprovalRequest.RequestType requestType,
            Object requestData,
            Object currentData,
            String changeSummary,
            String requestedBy,
            ApprovalRequest.Source source,
            String importLabel) {

        MDC.put("entityType", entityType.name());
        MDC.put("entityId", entityId);
        try {
            log.trace("Entering submitApprovalRequest() | entityType={} | entityId={} | requestType={} | correlationId={} | processId={}",
                    entityType, entityId, requestType, MDC.get("correlationId"), MDC.get("processId"));
            log.info("[submitApprovalRequest] START | entityType={} | entityId={} | requestType={} | requestedBy={} | source={} | importLabel={}",
                    entityType, entityId, requestType, requestedBy, source, importLabel);

            // Check if entity already has a pending approval
            log.debug("Checking for existing pending approval | entityType={} | entityId={}", entityType, entityId);
            Optional<ApprovalRequest> existing = approvalRequestRepository
                    .findPendingForEntity(entityType, entityId);
            log.trace("Existing pending approval check result: {} | correlationId={}",
                    existing.isPresent() ? "FOUND (ID=" + existing.get().getId() + ")" : "NOT_FOUND",
                    MDC.get("correlationId"));

            if (existing.isPresent()) {
                log.warn("Approval request already pending | entityType={} | entityId={} | existingRequestId={} | correlationId={}",
                        entityType, entityId, existing.get().getId(), MDC.get("correlationId"));
                throw new IllegalStateException(
                        String.format("Entity %s/%s already has a pending approval request (ID: %d)",
                                entityType, entityId, existing.get().getId())
                );
            }

            // Serialize request data to JSON
            log.trace("Serializing request data to JSON | entityType={} | entityId={}", entityType, entityId);
            String requestDataJson = serializeToJson(requestData);
            String currentDataJson = currentData != null ? serializeToJson(currentData) : null;
            log.debug("Serialization complete | requestDataJson length={} | currentDataJson length={}",
                    requestDataJson != null ? requestDataJson.length() : 0,
                    currentDataJson != null ? currentDataJson.length() : 0);

            // Create approval request
            log.trace("Creating approval request entity | entityType={} | entityId={}", entityType, entityId);
            ApprovalRequest request = ApprovalRequest.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .requestType(requestType)
                    .approvalStatus(ApprovalRequest.ApprovalStatus.PENDING_APPROVAL)
                    .requestedBy(requestedBy)
                    .requestData(requestDataJson)
                    .currentData(currentDataJson)
                    .changeSummary(changeSummary)
                    .source(source)
                    .importLabel(importLabel)
                    .build();

            log.trace("Saving approval request to database | entityType={} | entityId={}", entityType, entityId);
            request = approvalRequestRepository.save(request);
            log.debug("Approval request saved | requestId={} | correlationId={}", request.getId(), MDC.get("correlationId"));

            // Create SUBMIT action for audit trail
            log.trace("Creating SUBMIT action for audit trail | requestId={}", request.getId());
            ApprovalAction submitAction = ApprovalAction.submit(request, requestedBy);
            approvalActionRepository.save(submitAction);

            log.info("Approval request submitted: {} {} by {} (ID: {}) | correlationId={}",
                    entityType, entityId, requestedBy, request.getId(), MDC.get("correlationId"));
            log.trace("Exiting submitApprovalRequest() | requestId={} | success=true", request.getId());

            return request;
        } finally {
            MDC.remove("entityType");
            MDC.remove("entityId");
        }
    }

    // ===== Approve Request =====

    /**
     * Approve an approval request
     *
     * @param requestId Approval request ID
     * @param approvedBy User approving the request
     * @param justification Optional justification for approval
     * @return Approved request
     */
    @Transactional
    public ApprovalRequest approveRequest(Long requestId, String approvedBy, String justification) {
        MDC.put("approvalRequestId", String.valueOf(requestId));
        try {
            log.trace("Entering approveRequest() | requestId={} | approvedBy={} | correlationId={} | processId={}",
                    requestId, approvedBy, MDC.get("correlationId"), MDC.get("processId"));
            log.debug("Fetching approval request | requestId={}", requestId);
            ApprovalRequest request = getApprovalRequest(requestId);

            log.trace("Request fetched | requestId={} | entityType={} | entityId={} | currentStatus={}",
                    requestId, request.getEntityType(), request.getEntityId(), request.getApprovalStatus());

            // Validate state
            if (!request.isPending()) {
                log.warn("Cannot approve request - invalid status | requestId={} | currentStatus={} | correlationId={}",
                        requestId, request.getApprovalStatus(), MDC.get("correlationId"));
                throw new IllegalStateException(
                        String.format("Cannot approve request %d - current status: %s",
                                requestId, request.getApprovalStatus())
                );
            }

            // Approve the request
            log.debug("Approving request | requestId={} | approvedBy={}", requestId, approvedBy);
            request.approve(approvedBy);
            request = approvalRequestRepository.save(request);

            // Create APPROVE action for audit trail
            log.trace("Creating APPROVE action for audit trail | requestId={}", requestId);
            ApprovalAction approveAction = ApprovalAction.approve(request, approvedBy, justification);
            approvalActionRepository.save(approveAction);

            log.info("Approval request approved: ID {} by {} - {} | correlationId={}", requestId, approvedBy,
                    request.getEntityType() + "/" + request.getEntityId(), MDC.get("correlationId"));
            log.trace("Exiting approveRequest() | requestId={} | success=true", requestId);

            return request;
        } finally {
            MDC.remove("approvalRequestId");
        }
    }

    // ===== Reject Request =====

    /**
     * Reject an approval request
     *
     * @param requestId Approval request ID
     * @param rejectedBy User rejecting the request
     * @param rejectionReason Reason for rejection (required)
     * @return Rejected request
     */
    @Transactional
    public ApprovalRequest rejectRequest(Long requestId, String rejectedBy, String rejectionReason) {
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        ApprovalRequest request = getApprovalRequest(requestId);

        // Validate state
        if (!request.isPending()) {
            throw new IllegalStateException(
                    String.format("Cannot reject request %d - current status: %s",
                            requestId, request.getApprovalStatus())
            );
        }

        // Reject the request
        request.reject(rejectedBy, rejectionReason);
        request = approvalRequestRepository.save(request);

        // Create REJECT action for audit trail
        ApprovalAction rejectAction = ApprovalAction.reject(request, rejectedBy, rejectionReason);
        approvalActionRepository.save(rejectAction);

        log.info("Approval request rejected: ID {} by {} - {} - Reason: {}",
                requestId, rejectedBy, request.getEntityType() + "/" + request.getEntityId(),
                rejectionReason);

        return request;
    }

    // ===== Resubmit After Rejection =====

    /**
     * Resubmit a rejected approval request
     *
     * @param requestId Approval request ID
     * @param resubmittedBy User resubmitting the request
     * @param updatedRequestData Optional updated request data (if changes were made)
     * @param changeSummary Summary of changes made before resubmission
     * @return Resubmitted request
     */
    @Transactional
    public ApprovalRequest resubmitRequest(
            Long requestId,
            String resubmittedBy,
            Object updatedRequestData,
            String changeSummary) {

        ApprovalRequest request = getApprovalRequest(requestId);

        // Validate state
        if (!request.isRejected()) {
            throw new IllegalStateException(
                    String.format("Cannot resubmit request %d - current status: %s (must be REJECTED)",
                            requestId, request.getApprovalStatus())
            );
        }

        // Update request data if provided
        if (updatedRequestData != null) {
            String updatedDataJson = serializeToJson(updatedRequestData);
            request.setRequestData(updatedDataJson);
            request.setChangeSummary(changeSummary);
        }

        // Resubmit the request
        request.resubmit(resubmittedBy);
        request = approvalRequestRepository.save(request);

        // Create RESUBMIT action for audit trail
        ApprovalAction resubmitAction = ApprovalAction.resubmit(request, resubmittedBy, changeSummary);
        approvalActionRepository.save(resubmitAction);

        log.info("Approval request resubmitted: ID {} by {} - {}",
                requestId, resubmittedBy, request.getEntityType() + "/" + request.getEntityId());

        return request;
    }

    // ===== Revoke Approval =====

    /**
     * Revoke an approved request (move back to PENDING_APPROVAL)
     *
     * @param requestId Approval request ID
     * @param revokedBy User revoking the approval
     * @param revocationReason Reason for revoking (required)
     * @return Revoked request
     */
    @Transactional
    public ApprovalRequest revokeApproval(Long requestId, String revokedBy, String revocationReason) {
        if (revocationReason == null || revocationReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Revocation reason is required");
        }

        ApprovalRequest request = getApprovalRequest(requestId);

        // Validate state
        if (!request.isApproved()) {
            throw new IllegalStateException(
                    String.format("Cannot revoke request %d - current status: %s (must be APPROVED)",
                            requestId, request.getApprovalStatus())
            );
        }

        // Revoke the approval
        request.revokeApproval(revokedBy, revocationReason);
        request = approvalRequestRepository.save(request);

        // Create REVOKE action for audit trail
        ApprovalAction revokeAction = ApprovalAction.revoke(request, revokedBy, revocationReason);
        approvalActionRepository.save(revokeAction);

        log.info("Approval revoked: ID {} by {} - {} - Reason: {}",
                requestId, revokedBy, request.getEntityType() + "/" + request.getEntityId(),
                revocationReason);

        return request;
    }

    // ===== Query Methods =====

    /**
     * Get all pending approvals across all entity types
     */
    public List<ApprovalRequest> getAllPendingApprovals() {
        return approvalRequestRepository.findAllPending();
    }

    /**
     * Get all approved approvals
     */
    public List<ApprovalRequest> getAllApprovedApprovals() {
        return approvalRequestRepository.findByApprovalStatusOrderByRequestedAtDesc(
                ApprovalRequest.ApprovalStatus.APPROVED);
    }

    /**
     * Get all pending approvals for specific entity type
     */
    public List<ApprovalRequest> getPendingApprovalsByType(ApprovalRequest.EntityType entityType) {
        return approvalRequestRepository.findByEntityTypeAndApprovalStatusOrderByRequestedAtDesc(
                entityType, ApprovalRequest.ApprovalStatus.PENDING_APPROVAL);
    }

    /**
     * Get pending approval for specific entity
     */
    public Optional<ApprovalRequest> getPendingApprovalForEntity(
            ApprovalRequest.EntityType entityType,
            String entityId) {
        return approvalRequestRepository.findPendingForEntity(entityType, entityId);
    }

    /**
     * Get approval history for specific entity
     */
    public List<ApprovalRequest> getApprovalHistoryForEntity(
            ApprovalRequest.EntityType entityType,
            String entityId) {
        return approvalRequestRepository.findByEntityTypeAndEntityIdOrderByRequestedAtDesc(
                entityType, entityId);
    }

    /**
     * Get approval actions (audit trail) for request
     */
    public List<ApprovalAction> getApprovalActions(Long requestId) {
        return approvalActionRepository.findByApprovalRequestIdOrderByActionAtAsc(requestId);
    }

    /**
     * Get approval request by ID
     */
    public ApprovalRequest getApprovalRequest(Long requestId) {
        return approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Approval request not found: " + requestId));
    }

    /**
     * Check if entity has pending approval
     */
    public boolean hasPendingApproval(ApprovalRequest.EntityType entityType, String entityId) {
        return approvalRequestRepository.hasPendingApproval(entityType, entityId);
    }

    /**
     * Count pending approvals by entity type
     */
    public long countPendingApprovals(ApprovalRequest.EntityType entityType) {
        return approvalRequestRepository.countByEntityTypeAndApprovalStatus(
                entityType, ApprovalRequest.ApprovalStatus.PENDING_APPROVAL);
    }

    /**
     * Count all pending approvals
     */
    public long countAllPendingApprovals() {
        return approvalRequestRepository.countAllPending();
    }

    // ===== Helper Methods =====

    /**
     * Serialize object to JSON string
     */
    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON", e);
            throw new RuntimeException("Failed to serialize request data", e);
        }
    }

    /**
     * Deserialize JSON string to specified class
     */
    public <T> T deserializeFromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to {}", clazz.getName(), e);
            throw new RuntimeException("Failed to deserialize request data", e);
        }
    }
}
