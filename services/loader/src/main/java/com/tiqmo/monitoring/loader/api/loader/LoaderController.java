package com.tiqmo.monitoring.loader.api.loader;

import com.tiqmo.monitoring.loader.domain.approval.entity.ApprovalRequest;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderArchive;
import com.tiqmo.monitoring.loader.domain.loader.entity.PurgeStrategy;
import com.tiqmo.monitoring.loader.domain.loader.entity.SourceDatabase;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.SourceDatabaseRepository;
import com.tiqmo.monitoring.loader.dto.approval.ApprovalRequestDto;
import com.tiqmo.monitoring.loader.dto.loader.ActivityEventDto;
import com.tiqmo.monitoring.loader.dto.loader.EtlLoaderDto;
import com.tiqmo.monitoring.loader.dto.loader.LoadersStatsDto;
import com.tiqmo.monitoring.loader.dto.loader.SourceDatabaseDto;
import com.tiqmo.monitoring.loader.dto.loader.TestQueryRequest;
import com.tiqmo.monitoring.loader.dto.loader.TestQueryResponse;
import com.tiqmo.monitoring.loader.infra.db.SourceDbManager;
import com.tiqmo.monitoring.loader.service.loader.LoaderService;
import com.tiqmo.monitoring.loader.service.security.FieldProtectionService;
import com.tiqmo.monitoring.loader.service.security.HateoasService;
import com.tiqmo.monitoring.loader.service.versioning.LoaderApprovalService;
import com.tiqmo.monitoring.loader.service.versioning.LoaderArchiveService;
import com.tiqmo.monitoring.loader.service.versioning.LoaderDraftService;
import com.tiqmo.monitoring.loader.service.versioning.LoaderRejectionService;
import com.tiqmo.monitoring.workflow.domain.ChangeType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/res/loaders")
@RequiredArgsConstructor
@Slf4j
public class LoaderController {

    private final LoaderService service;
    private final FieldProtectionService fieldProtectionService;
    private final HateoasService hateoasService;
    private final LoaderRepository loaderRepository;
    private final SourceDatabaseRepository sourceDbRepo;
    private final SourceDbManager sourceDbManager;

    // New versioning services
    private final LoaderDraftService loaderDraftService;
    private final LoaderApprovalService loaderApprovalService;
    private final LoaderRejectionService loaderRejectionService;
    private final LoaderArchiveService loaderArchiveService;

    @GetMapping("/loaders")
    public ResponseEntity<Map<String, Object>> getAll(Authentication authentication) {
        // Get all loaders from service
        List<EtlLoaderDto> loaders = service.findAll();

        // Extract user role from authentication
        String role = extractUserRole(authentication);

        log.debug("Filtering {} loaders for role: {}", loaders.size(), role);

        // Apply field-level protection based on role
        List<Map<String, Object>> filteredLoaders = fieldProtectionService.filterFields(
                loaders,
                "LOADER",
                role
        );

        // Add HATEOAS _links to each loader
        for (Map<String, Object> loader : filteredLoaders) {
            String loaderCode = (String) loader.get("loaderCode");
            String approvalStatus = (String) loader.get("approvalStatus");
            Boolean enabled = (Boolean) loader.get("enabled");

            if (loaderCode != null) {
                String resourceState = hateoasService.getLoaderState(approvalStatus, enabled);
                Map<String, Map<String, String>> links = hateoasService.buildLinks(
                        loaderCode,
                        "LOADER",
                        resourceState,
                        role
                );
                loader.put("_links", links);
            }
        }

        // Get list of protected fields for visual marking in frontend
        List<String> protectedFields = fieldProtectionService.getProtectedFields("LOADER", role);

        log.debug("Returning {} filtered loaders with {} protected fields to role: {}",
                filteredLoaders.size(), protectedFields.size(), role);

        return ResponseEntity.ok(Map.of(
                "loaders", filteredLoaders,
                "_protectedFields", protectedFields
        ));
    }

    @GetMapping("/{loaderCode}")
    public ResponseEntity<?> getByCode(@PathVariable String loaderCode, Authentication authentication) {
        EtlLoaderDto loader = service.getByCode(loaderCode);
        if (loader == null) {
            return ResponseEntity.notFound().build();
        }

        // Extract user role from authentication
        String role = extractUserRole(authentication);

        log.debug("Filtering loader '{}' for role: {}", loaderCode, role);

        // Apply field-level protection based on role
        Map<String, Object> filteredLoader = fieldProtectionService.filterFields(
                loader,
                "LOADER",
                role
        );

        // Add HATEOAS _links
        String approvalStatus = (String) filteredLoader.get("approvalStatus");
        Boolean enabled = (Boolean) filteredLoader.get("enabled");
        String resourceState = hateoasService.getLoaderState(approvalStatus, enabled);
        Map<String, Map<String, String>> links = hateoasService.buildLinks(
                loaderCode,
                "LOADER",
                resourceState,
                role
        );
        filteredLoader.put("_links", links);

        return ResponseEntity.ok(filteredLoader);
    }

    /**
     * Create a new loader draft.
     * Creates a DRAFT version that requires approval before becoming ACTIVE.
     *
     * <p><b>Workflow:</b> DRAFT → (submit) → PENDING_APPROVAL → (approve) → ACTIVE
     *
     * @param dto Loader configuration
     * @param authentication Spring Security authentication
     * @return Created draft entity
     */
    @PostMapping
    public ResponseEntity<EtlLoaderDto> create(
            @Valid @RequestBody EtlLoaderDto dto,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            log.info("Creating loader draft: {} by user: {}", dto.getLoaderCode(), username);

            // Convert DTO to entity
            Loader draftData = fromDto(dto);

            // Create draft using versioning service
            Loader draft = loaderDraftService.createDraft(
                    draftData,
                    username,
                    ChangeType.MANUAL_EDIT,
                    null  // no import label for manual creation
            );

            log.info("Loader draft created: {} version: {}", draft.getLoaderCode(), draft.getVersionNumber());

            // Convert back to DTO
            EtlLoaderDto response = service.getByCode(draft.getLoaderCode());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Failed to create loader draft: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Update an existing loader draft.
     * If a draft exists, updates it (cumulative draft). Otherwise creates a new draft.
     *
     * @param loaderCode Loader code
     * @param dto Updated loader configuration
     * @param authentication Spring Security authentication
     * @return Updated draft entity
     */
    @PutMapping("/{loaderCode}")
    public ResponseEntity<EtlLoaderDto> update(
            @PathVariable String loaderCode,
            @Valid @RequestBody EtlLoaderDto dto,
            Authentication authentication) {

        try {
            // Ensure path variable matches request body
            if (!loaderCode.equals(dto.getLoaderCode())) {
                dto.setLoaderCode(loaderCode);
            }

            String username = authentication.getName();
            log.info("Updating loader draft: {} by user: {}", loaderCode, username);

            // Convert DTO to entity
            Loader draftData = fromDto(dto);

            // Create or update draft
            Loader draft = loaderDraftService.createDraft(
                    draftData,
                    username,
                    ChangeType.MANUAL_EDIT,
                    null
            );

            log.info("Loader draft updated: {} version: {}", draft.getLoaderCode(), draft.getVersionNumber());

            // Convert back to DTO
            EtlLoaderDto response = service.getByCode(draft.getLoaderCode());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Failed to update loader draft: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<LoadersStatsDto> getStats() {
        LoadersStatsDto stats = service.getStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/activity")
    public ResponseEntity<List<ActivityEventDto>> getActivity(
            @RequestParam(defaultValue = "5") int limit) {
        List<ActivityEventDto> activity = service.getRecentActivity(limit);
        return ResponseEntity.ok(activity);
    }

    /**
     * Get list of source databases for form selection.
     * Returns ID, dbCode, dbType, ip, port, dbName (password excluded).
     */
    @GetMapping("/source-databases")
    public ResponseEntity<List<SourceDatabaseDto>> getSourceDatabases() {
        log.debug("Fetching source databases list");
        List<SourceDatabase> sourceDbs = sourceDbRepo.findAll();

        List<SourceDatabaseDto> dtos = sourceDbs.stream()
                .map(this::toSourceDatabaseDto)
                .toList();

        log.info("Returning {} source databases", dtos.size());
        return ResponseEntity.ok(dtos);
    }

    // ==================== APPROVAL WORKFLOW ====================

    /**
     * Submit loader draft for approval.
     * Changes status from DRAFT → PENDING_APPROVAL.
     *
     * @param loaderCode Loader code
     * @param authentication Spring Security authentication
     * @return Updated loader DTO
     */
    @PostMapping("/{loaderCode}/submit")
    public ResponseEntity<EtlLoaderDto> submitForApproval(
            @PathVariable String loaderCode,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            log.info("Submit for approval: {} by user: {}", loaderCode, username);

            // Find draft by loader code
            Loader draft = loaderRepository.findDraftByLoaderCode(loaderCode)
                    .orElseThrow(() -> new IllegalArgumentException("No draft found for loader: " + loaderCode));

            // Submit for approval
            loaderDraftService.submitForApproval(draft.getId(), username);

            log.info("Loader submitted for approval: {}", loaderCode);

            // Return updated DTO
            EtlLoaderDto response = service.getByCode(loaderCode);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Failed to submit loader for approval: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Approves a pending loader draft.
     * Archives the existing ACTIVE version and promotes draft to ACTIVE.
     *
     * <p><b>Security:</b> Requires ADMIN role.
     *
     * <p><b>State Transition:</b> PENDING_APPROVAL → ACTIVE
     *
     * <p>Request body example:
     * <pre>
     * {
     *   "comments": "Verified SQL query safety and configuration"
     * }
     * </pre>
     *
     * @param loaderCode Loader code to approve
     * @param request Request body with optional comments
     * @param authentication Spring Security authentication
     * @return Updated loader DTO
     */
    @PostMapping("/{loaderCode}/approve")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EtlLoaderDto> approveLoader(
            @PathVariable String loaderCode,
            @RequestBody(required = false) Map<String, String> request,
            Authentication authentication) {

        try {
            String username = authentication.getName();
            String comments = request != null ? request.get("comments") : "";

            log.info("Approve request for loader: {} by user: {}", loaderCode, username);

            // Find PENDING_APPROVAL draft
            Loader draft = loaderRepository.findDraftByLoaderCode(loaderCode)
                    .orElseThrow(() -> new IllegalArgumentException("No pending draft found for loader: " + loaderCode));

            // Approve using versioning service
            loaderApprovalService.approveDraft(draft.getId(), username, comments);

            log.info("Loader approved: {}", loaderCode);

            // Return updated DTO
            EtlLoaderDto response = service.getByCode(loaderCode);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Failed to approve loader: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Rejects a pending loader draft.
     * Archives the rejected draft with rejection metadata and deletes from main table.
     *
     * <p><b>Security:</b> Requires ADMIN role.
     *
     * <p><b>State Transition:</b> PENDING_APPROVAL → ARCHIVED (with rejection metadata)
     *
     * <p>Request body example:
     * <pre>
     * {
     *   "rejectionReason": "SQL query contains unsafe operations",
     *   "comments": "Please remove DELETE statements and resubmit"
     * }
     * </pre>
     *
     * @param loaderCode Loader code to reject
     * @param request Request body with rejection reason (required) and optional comments
     * @param authentication Spring Security authentication
     * @return Success message
     */
    @PostMapping("/{loaderCode}/reject")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectLoader(
            @PathVariable String loaderCode,
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        try {
            String username = authentication.getName();
            String rejectionReason = request.get("rejectionReason");

            log.info("Reject request for loader: {} by user: {}", loaderCode, username);

            // Validate rejection reason
            if (rejectionReason == null || rejectionReason.isBlank()) {
                log.warn("Rejection reason is required");
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "VALIDATION_REQUIRED_FIELD",
                        "message", "Rejection reason is required",
                        "field", "rejectionReason"
                ));
            }

            // Find PENDING_APPROVAL draft
            Loader draft = loaderRepository.findDraftByLoaderCode(loaderCode)
                    .orElseThrow(() -> new IllegalArgumentException("No pending draft found for loader: " + loaderCode));

            // Reject using versioning service
            loaderRejectionService.rejectDraft(draft.getId(), username, rejectionReason);

            log.info("Loader rejected: {}", loaderCode);

            return ResponseEntity.ok(Map.of(
                    "message", "Loader draft rejected and archived",
                    "loaderCode", loaderCode,
                    "rejectedBy", username
            ));

        } catch (IllegalArgumentException e) {
            log.error("Failed to reject loader: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a draft loader.
     * Only drafts (DRAFT or PENDING_APPROVAL) can be deleted. ACTIVE loaders require deletion approval.
     *
     * @param loaderCode Loader code to delete
     * @param authentication Spring Security authentication
     * @return Success message
     */
    @DeleteMapping("/{loaderCode}/draft")
    public ResponseEntity<?> deleteDraft(
            @PathVariable String loaderCode,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            log.info("Delete draft request for loader: {} by user: {}", loaderCode, username);

            // Find draft
            Loader draft = loaderRepository.findDraftByLoaderCode(loaderCode)
                    .orElseThrow(() -> new IllegalArgumentException("No draft found for loader: " + loaderCode));

            // Delete using draft service
            loaderDraftService.deleteDraft(draft.getId(), username);

            log.info("Loader draft deleted: {}", loaderCode);

            return ResponseEntity.ok(Map.of(
                    "message", "Draft deleted successfully",
                    "loaderCode", loaderCode
            ));

        } catch (IllegalArgumentException e) {
            log.error("Failed to delete draft: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get version history for a loader.
     * Returns all archived versions sorted by version number descending.
     *
     * @param loaderCode Loader code
     * @return List of archived versions
     */
    @GetMapping("/{loaderCode}/versions")
    public ResponseEntity<?> getVersionHistory(@PathVariable String loaderCode) {
        try {
            log.info("Fetching version history for loader: {}", loaderCode);

            // Get archived versions using public method
            List<Loader> archivedVersions = loaderArchiveService.getArchivedVersions(loaderCode);

            // Convert to DTOs (simplified for version history)
            List<Map<String, Object>> versions = archivedVersions.stream()
                    .map(loader -> {
                        Map<String, Object> versionMap = new java.util.HashMap<>();
                        versionMap.put("versionNumber", loader.getVersionNumber());
                        versionMap.put("versionStatus", loader.getVersionStatus().toString());
                        versionMap.put("createdAt", loader.getCreatedAt());
                        versionMap.put("createdBy", loader.getCreatedBy());
                        versionMap.put("modifiedAt", loader.getModifiedAt() != null ? loader.getModifiedAt() : loader.getCreatedAt());
                        versionMap.put("modifiedBy", loader.getModifiedBy() != null ? loader.getModifiedBy() : loader.getCreatedBy());
                        versionMap.put("changeType", loader.getChangeType() != null ? loader.getChangeType().toString() : "");
                        versionMap.put("changeSummary", loader.getChangeSummary() != null ? loader.getChangeSummary() : "");
                        return versionMap;
                    })
                    .toList();

            log.info("Found {} archived versions for loader: {}", versions.size(), loaderCode);

            return ResponseEntity.ok(Map.of(
                    "loaderCode", loaderCode,
                    "versions", versions,
                    "totalVersions", versions.size()
            ));

        } catch (Exception e) {
            log.error("Failed to fetch version history: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get a specific version of a loader.
     *
     * @param loaderCode Loader code
     * @param versionNumber Version number
     * @return Loader version details
     */
    @GetMapping("/{loaderCode}/versions/{versionNumber}")
    public ResponseEntity<?> getVersion(
            @PathVariable String loaderCode,
            @PathVariable Integer versionNumber) {
        try {
            log.info("Fetching version {} for loader: {}", versionNumber, loaderCode);

            // Get specific version from archive using public method
            Loader version = loaderArchiveService.getArchivedVersion(loaderCode, versionNumber);

            if (version == null) {
                return ResponseEntity.notFound().build();
            }

            // Convert to DTO using service's public method
            EtlLoaderDto dto = service.getByCode(loaderCode);

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("Failed to fetch version: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private SourceDatabaseDto toSourceDatabaseDto(SourceDatabase db) {
        return SourceDatabaseDto.builder()
                .id(db.getId())
                .dbCode(db.getDbCode())
                .ip(db.getIp())
                .port(db.getPort())
                .dbName(db.getDbName())
                .dbType(db.getDbType() != null ? db.getDbType().name() : null)
                .userName(db.getUserName())
                // Password intentionally excluded for security
                .build();
    }

    /**
     * Test SQL query against a source database.
     * Validates syntax and execution before creating/updating a loader.
     *
     * <p>Security: Available to all authenticated users (ADMIN, OPERATOR, VIEWER)
     * since testing a query doesn't modify data.
     *
     * <p>Executes the query with LIMIT 10 to prevent large result sets.
     *
     * @param request Test query request containing sourceDatabaseId and sql
     * @return Test query response with results or errors
     */
    @PostMapping("/test-query")
    public ResponseEntity<TestQueryResponse> testQuery(@Valid @RequestBody TestQueryRequest request) {
        long startTime = System.currentTimeMillis();

        // EXTENSIVE DEBUG LOGGING - Remove after debugging
        log.info("========== TEST-QUERY ENDPOINT CALLED ==========");
        log.info("DEBUG [LoaderController]: /test-query endpoint invoked at {}", new java.util.Date());
        log.info("DEBUG [LoaderController]: Request object: {}", request != null ? "NOT NULL" : "NULL");

        if (request != null) {
            log.info("DEBUG [LoaderController]: sourceDatabaseId = {}", request.getSourceDatabaseId());
            log.info("DEBUG [LoaderController]: SQL length = {}", request.getSql() != null ? request.getSql().length() : "NULL");
            log.info("DEBUG [LoaderController]: SQL query (first 200 chars) = {}",
                request.getSql() != null && request.getSql().length() > 0
                    ? request.getSql().substring(0, Math.min(200, request.getSql().length()))
                    : "EMPTY");
        }
        log.info("================================================");

        try {
            log.info("Testing SQL query | sourceDatabaseId={} | sqlLength={}",
                    request.getSourceDatabaseId(), request.getSql().length());

            // Find source database by ID
            SourceDatabase sourceDb = sourceDbRepo.findById(request.getSourceDatabaseId())
                    .orElseThrow(() -> new RuntimeException("Source database not found with ID: " + request.getSourceDatabaseId()));

            String sql = request.getSql().trim();

            // Security: Ensure query is SELECT only (read-only)
            String upperSql = sql.toUpperCase();
            if (!upperSql.startsWith("SELECT")) {
                return ResponseEntity.ok(TestQueryResponse.builder()
                        .success(false)
                        .message("Query must be a SELECT statement (read-only)")
                        .errors(List.of("Loader SQL must start with SELECT", "Write operations are not allowed"))
                        .build());
            }

            // REMOVED: Dangerous keyword validation - it was blocking valid column names like "create_date", "update_date"
            // The database has read-only permissions, which is the real security layer
            // Approval workflow will handle SQL review by human approvers

            // Replace time parameters with test values (12 minutes ago to 2 minutes ago)
            // This allows testing loader SQL without manually entering dates
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime fromTime = now.minusMinutes(12);
            java.time.LocalDateTime toTime = now.minusMinutes(2);

            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            String fromTimeStr = fromTime.format(formatter);
            String toTimeStr = toTime.format(formatter);

            // Replace placeholders
            String testSql = sql.replace(":fromTime", fromTimeStr)
                                .replace(":toTime", toTimeStr);

            log.info("Replaced time parameters: :fromTime={}, :toTime={}", fromTimeStr, toTimeStr);

            // Add LIMIT 10 to prevent large result sets during testing
            if (!upperSql.contains("LIMIT")) {
                testSql = testSql + " LIMIT 10";
                log.info("Added LIMIT 10 to test query");
            }

            // Log the final SQL that will be executed
            log.info("========== EXECUTING SQL QUERY ==========");
            log.info("Final SQL query to execute:\n{}", testSql);
            log.info("=========================================");

            // Execute query against source database
            List<Map<String, Object>> results = sourceDbManager.runQuery(sourceDb.getDbCode(), testSql);

            long executionTime = System.currentTimeMillis() - startTime;

            log.info("Query test successful | sourceDatabaseId={} | rowCount={} | executionTimeMs={}",
                    request.getSourceDatabaseId(), results.size(), executionTime);

            TestQueryResponse response = TestQueryResponse.builder()
                    .success(true)
                    .message("Query executed successfully")
                    .rowCount(results.size())
                    .totalRowCount(results.size())
                    .executionTimeMs(executionTime)
                    .sampleData(results)
                    .build();

            // DEBUG: Remove after debugging
            log.info("DEBUG [LoaderController]: Returning success response - rowCount={}, executionTime={}ms",
                    results.size(), executionTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;

            log.error("Query test failed | sourceDatabaseId={} | executionTimeMs={} | error={}",
                    request.getSourceDatabaseId(), executionTime, e.getMessage(), e);

            return ResponseEntity.ok(TestQueryResponse.builder()
                    .success(false)
                    .message("Query execution failed: " + e.getMessage())
                    .errors(List.of(e.getMessage()))
                    .executionTimeMs(executionTime)
                    .build());
        }
    }

    /**
     * Extract user role from Spring Security Authentication.
     * Handles both "ROLE_ADMIN" and "ADMIN" formats.
     *
     * @param authentication The authentication object
     * @return The role code (e.g., "ADMIN", "OPERATOR", "VIEWER")
     */
    private String extractUserRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            log.warn("No authentication found, defaulting to VIEWER role");
            return "VIEWER"; // Default to most restrictive
        }

        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_VIEWER");

        // Remove "ROLE_" prefix if present
        return fieldProtectionService.extractRoleCode(role);
    }

    /**
     * Convert EtlLoaderDto to Loader entity.
     * Fetches SourceDatabase by Code (Excel import) or ID (UI form) and sets it on the Loader entity.
     *
     * @param dto EtlLoaderDto
     * @return Loader entity
     * @throws IllegalArgumentException if source database not found
     */
    private Loader fromDto(EtlLoaderDto dto) {
        // Fetch source database by Code (Excel import) or ID (UI form)
        // Priority: sourceDatabaseCode > sourceDatabaseId
        SourceDatabase sourceDb = null;

        if (dto.getSourceDatabaseCode() != null && !dto.getSourceDatabaseCode().trim().isEmpty()) {
            // Excel import path - lookup by database code
            String dbCode = dto.getSourceDatabaseCode().trim();
            sourceDb = sourceDbRepo.findByDbCode(dbCode)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Source database not found with code: " + dbCode));
            log.debug("Resolved source database by code: {} -> ID: {}", dbCode, sourceDb.getId());
        } else if (dto.getSourceDatabaseId() != null) {
            // UI form path - lookup by database ID
            sourceDb = sourceDbRepo.findById(dto.getSourceDatabaseId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Source database not found with ID: " + dto.getSourceDatabaseId()));
            log.debug("Resolved source database by ID: {}", dto.getSourceDatabaseId());
        } else {
            throw new IllegalArgumentException(
                    "Either sourceDatabaseCode or sourceDatabaseId must be provided");
        }

        // Convert purgeStrategy String to enum (if provided)
        PurgeStrategy purgeStrategy = null;
        if (dto.getPurgeStrategy() != null && !dto.getPurgeStrategy().isEmpty()) {
            try {
                purgeStrategy = PurgeStrategy.valueOf(dto.getPurgeStrategy());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid purge strategy: " + dto.getPurgeStrategy());
            }
        }

        // Build Loader entity
        return Loader.builder()
                .loaderCode(dto.getLoaderCode())
                .loaderSql(dto.getLoaderSql())
                .minIntervalSeconds(dto.getMinIntervalSeconds())
                .maxIntervalSeconds(dto.getMaxIntervalSeconds())
                .maxQueryPeriodSeconds(dto.getMaxQueryPeriodSeconds())
                .maxParallelExecutions(dto.getMaxParallelExecutions())
                .sourceTimezoneOffsetHours(dto.getSourceTimezoneOffsetHours())
                .enabled(dto.getEnabled() != null ? dto.getEnabled() : true)
                .aggregationPeriodSeconds(dto.getAggregationPeriodSeconds())
                .purgeStrategy(purgeStrategy)
                .sourceDatabase(sourceDb)
                .build();
    }
}
