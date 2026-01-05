package com.tiqmo.monitoring.loader.api.approval;

import com.tiqmo.monitoring.loader.domain.approval.entity.ApprovalAction;
import com.tiqmo.monitoring.loader.domain.approval.entity.ApprovalRequest;
import com.tiqmo.monitoring.loader.dto.approval.*;
import com.tiqmo.monitoring.loader.infra.config.ApiKey;
import com.tiqmo.monitoring.loader.service.approval.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service ID: ldr (Loader Service), Controller ID: apv (Approval Controller)
 *
 * <p>Approval Management Controller
 *
 * <p>REST API endpoints for generic approval workflow.
 * Supports approval management for all entity types (LOADER, DASHBOARD, INCIDENT, CHART).
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@RestController
@RequestMapping("/api/v1/ldr/apv")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Approvals", description = "Generic approval workflow management")
@SecurityRequirement(name = "bearer-jwt")
public class ApprovalController {

    private final ApprovalService approvalService;

    // ===== Submit Endpoint =====

    /**
     * Submit a new approval request
     */
    @PostMapping("/submit")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Submit approval request", description = "Submit a new approval request for any entity type")
    @ApiKey(value = "ldr.approval.submit", description = "Submit a new approval request")
    public ResponseEntity<ApprovalRequestDto> submitApprovalRequest(
            @Valid @RequestBody SubmitApprovalRequestDto request) {
        log.info("[submitApprovalRequest] request: {}", request);
        ApprovalRequest.EntityType entityType = ApprovalRequest.EntityType.valueOf(
                request.getEntityType().toUpperCase());
        log.info("[submitApprovalRequest] entityType: {}", entityType);
        ApprovalRequest.RequestType requestType = ApprovalRequest.RequestType.valueOf(
                request.getRequestType().toUpperCase());
        log.info("[submitApprovalRequest] entityType: {}", requestType);
        ApprovalRequest.Source source = request.getSource() != null ?
                ApprovalRequest.Source.valueOf(request.getSource().toUpperCase()) : null;
        log.info("[submitApprovalRequest] source: {}", source);


        ApprovalRequest approvalRequest = approvalService.submitApprovalRequest(
                entityType,
                request.getEntityId(),
                requestType,
                request.getRequestData(),
                request.getCurrentData(),
                request.getChangeSummary(),
                request.getRequestedBy(),
                source,
                request.getImportLabel()
        );

        log.info("[submitApprovalRequest] approvalService.submitApprovalRequest: {}", approvalRequest);
        return ResponseEntity.ok(ApprovalRequestDto.fromEntity(approvalRequest));
    }

    // ===== Query Endpoints =====

    /**
     * Get all pending approvals (admin only)
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all pending approvals", description = "Returns all pending approval requests across all entity types")
    @ApiKey(value = "ldr.approval.pending", description = "Get all pending approvals", tags = {"admin"})
    public ResponseEntity<List<ApprovalRequestDto>> getAllPendingApprovals() {
        List<ApprovalRequest> requests = approvalService.getAllPendingApprovals();
        List<ApprovalRequestDto> dtos = requests.stream()
                .map(ApprovalRequestDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get all approved approvals (admin only)
     */
    @GetMapping("/approved")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all approved approvals", description = "Returns all approved approval requests across all entity types")
    @ApiKey(value = "ldr.approval.approved", description = "Get all approved approvals", tags = {"admin"})
    public ResponseEntity<List<ApprovalRequestDto>> getAllApprovedApprovals() {
        log.info("[ISSUE_APPROVAL_PAGE] GET /api/approvals/approved endpoint reached - fetching approved approvals");
        List<ApprovalRequest> requests = approvalService.getAllApprovedApprovals();
        log.info("[ISSUE_APPROVAL_PAGE] Found {} approved approvals", requests.size());
        List<ApprovalRequestDto> dtos = requests.stream()
                .map(ApprovalRequestDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Count pending approvals
     * NOTE: This must be declared BEFORE /{requestId} to avoid path conflict
     */
    @GetMapping("/pending/count")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Count all pending approvals", description = "Returns count of pending approvals across all entity types")
    @ApiKey(value = "ldr.approval.pendingCount", description = "Count pending approvals", tags = {"admin"})
    public ResponseEntity<Long> countPendingApprovals(
            @Parameter(description = "Filter by entity type (optional)")
            @RequestParam(required = false) String entityType) {

        if (entityType != null) {
            ApprovalRequest.EntityType type = ApprovalRequest.EntityType.valueOf(entityType.toUpperCase());
            return ResponseEntity.ok(approvalService.countPendingApprovals(type));
        }
        return ResponseEntity.ok(approvalService.countAllPendingApprovals());
    }

    /**
     * Get pending approvals by entity type
     * NOTE: This must be declared BEFORE /{requestId} to avoid path conflict
     */
    @GetMapping("/pending/{entityType}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get pending approvals by entity type", description = "Returns pending approvals for specific entity type (LOADER, DASHBOARD, etc.)")
    @ApiKey(value = "ldr.approval.pendingByType", description = "Get pending approvals by entity type", tags = {"admin"})
    public ResponseEntity<List<ApprovalRequestDto>> getPendingApprovalsByType(
            @Parameter(description = "Entity type (LOADER, DASHBOARD, INCIDENT, CHART, ALERT_RULE)")
            @PathVariable String entityType) {

        ApprovalRequest.EntityType type = ApprovalRequest.EntityType.valueOf(entityType.toUpperCase());
        List<ApprovalRequest> requests = approvalService.getPendingApprovalsByType(type);
        List<ApprovalRequestDto> dtos = requests.stream()
                .map(ApprovalRequestDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get approval history for specific entity
     * NOTE: This must be declared BEFORE /{requestId} to avoid path conflict
     */
    @GetMapping("/history/{entityType}/{entityId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Get approval history for entity", description = "Returns complete approval history for specific entity")
    @ApiKey(value = "ldr.approval.history", description = "Get approval history for entity")
    public ResponseEntity<List<ApprovalRequestDto>> getApprovalHistory(
            @Parameter(description = "Entity type")
            @PathVariable String entityType,
            @Parameter(description = "Entity ID (e.g., loader code)")
            @PathVariable String entityId) {

        ApprovalRequest.EntityType type = ApprovalRequest.EntityType.valueOf(entityType.toUpperCase());
        List<ApprovalRequest> requests = approvalService.getApprovalHistoryForEntity(type, entityId);
        List<ApprovalRequestDto> dtos = requests.stream()
                .map(ApprovalRequestDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get action audit trail for approval request
     * NOTE: This must be declared BEFORE /{requestId} to avoid path conflict
     */
    @GetMapping("/{requestId}/actions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Get action audit trail", description = "Returns complete audit trail of all actions taken on approval request")
    @ApiKey(value = "ldr.approval.actions", description = "Get action audit trail for approval")
    public ResponseEntity<List<ApprovalActionDto>> getApprovalActions(
            @Parameter(description = "Approval request ID")
            @PathVariable Long requestId) {

        List<ApprovalAction> actions = approvalService.getApprovalActions(requestId);
        List<ApprovalActionDto> dtos = actions.stream()
                .map(ApprovalActionDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get approval request by ID
     * IMPORTANT: This catch-all endpoint must be declared LAST to avoid matching more specific paths
     */
    @GetMapping("/{requestId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Get approval request details", description = "Returns approval request with full details including action audit trail")
    @ApiKey(value = "ldr.approval.get", description = "Get approval request by ID")
    public ResponseEntity<ApprovalRequestDto> getApprovalRequest(
            @Parameter(description = "Approval request ID")
            @PathVariable Long requestId,
            @Parameter(description = "Include action audit trail")
            @RequestParam(defaultValue = "false") boolean includeActions) {

        log.warn("[ISSUE_APPROVAL_PAGE] GET /api/approvals/{} endpoint (catch-all) reached - THIS SHOULD NOT MATCH '/approved'!", requestId);
        ApprovalRequest request = approvalService.getApprovalRequest(requestId);
        ApprovalRequestDto dto = ApprovalRequestDto.fromEntity(request);

        if (includeActions) {
            List<ApprovalAction> actions = approvalService.getApprovalActions(requestId);
            List<ApprovalActionDto> actionDtos = actions.stream()
                    .map(ApprovalActionDto::fromEntity)
                    .collect(Collectors.toList());
            dto.setActions(actionDtos);
        }

        return ResponseEntity.ok(dto);
    }

    // ===== Action Endpoints =====

    /**
     * Approve an approval request
     */
    @PostMapping("/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve request", description = "Approve a pending approval request")
    @ApiKey(value = "ldr.approval.approve", description = "Approve a pending request", tags = {"admin"})
    public ResponseEntity<ApprovalRequestDto> approveRequest(
            @Valid @RequestBody ApproveRequestDto request,
            Authentication authentication) {

        String approvedBy = authentication.getName();
        ApprovalRequest approved = approvalService.approveRequest(
                request.getRequestId(),
                approvedBy,
                request.getJustification()
        );

        return ResponseEntity.ok(ApprovalRequestDto.fromEntity(approved));
    }

    /**
     * Reject an approval request
     */
    @PostMapping("/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject request", description = "Reject a pending approval request (requires justification)")
    @ApiKey(value = "ldr.approval.reject", description = "Reject a pending request", tags = {"admin"})
    public ResponseEntity<ApprovalRequestDto> rejectRequest(
            @Valid @RequestBody RejectRequestDto request,
            Authentication authentication) {

        String rejectedBy = authentication.getName();
        ApprovalRequest rejected = approvalService.rejectRequest(
                request.getRequestId(),
                rejectedBy,
                request.getRejectionReason()
        );

        return ResponseEntity.ok(ApprovalRequestDto.fromEntity(rejected));
    }

    /**
     * Resubmit a rejected approval request
     */
    @PostMapping("/resubmit")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Resubmit request", description = "Resubmit a rejected approval request with optional changes")
    @ApiKey(value = "ldr.approval.resubmit", description = "Resubmit a rejected request")
    public ResponseEntity<ApprovalRequestDto> resubmitRequest(
            @Valid @RequestBody ResubmitRequestDto request,
            Authentication authentication) {

        String resubmittedBy = authentication.getName();
        ApprovalRequest resubmitted = approvalService.resubmitRequest(
                request.getRequestId(),
                resubmittedBy,
                request.getUpdatedRequestData(),
                request.getChangeSummary()
        );

        return ResponseEntity.ok(ApprovalRequestDto.fromEntity(resubmitted));
    }

    /**
     * Revoke an approved request
     */
    @PostMapping("/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Revoke approval", description = "Revoke an approved request (requires justification)")
    @ApiKey(value = "ldr.approval.revoke", description = "Revoke an approved request", tags = {"admin"})
    public ResponseEntity<ApprovalRequestDto> revokeApproval(
            @Valid @RequestBody RevokeApprovalDto request,
            Authentication authentication) {

        String revokedBy = authentication.getName();
        ApprovalRequest revoked = approvalService.revokeApproval(
                request.getRequestId(),
                revokedBy,
                request.getRevocationReason()
        );

        return ResponseEntity.ok(ApprovalRequestDto.fromEntity(revoked));
    }
}
