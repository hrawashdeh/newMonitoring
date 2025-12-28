package com.tiqmo.monitoring.loader.api.loader;

import com.tiqmo.monitoring.loader.domain.loader.entity.SourceDatabase;
import com.tiqmo.monitoring.loader.domain.loader.repo.SourceDatabaseRepository;
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
    private final SourceDatabaseRepository sourceDbRepo;
    private final SourceDbManager sourceDbManager;

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

    @PostMapping
    public ResponseEntity<EtlLoaderDto> create(@Valid @RequestBody EtlLoaderDto dto) {
        try {
            EtlLoaderDto created = service.create(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PutMapping("/{loaderCode}")
    public ResponseEntity<EtlLoaderDto> update(
            @PathVariable String loaderCode,
            @Valid @RequestBody EtlLoaderDto dto) {

        // Ensure path variable matches request body
        if (!loaderCode.equals(dto.getLoaderCode())) {
            dto.setLoaderCode(loaderCode);
        }

        EtlLoaderDto updated = service.upsert(dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{loaderCode}")
    public ResponseEntity<Void> delete(@PathVariable String loaderCode) {
        try {
            service.deleteByCode(loaderCode);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
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
     * Approves a pending loader.
     *
     * <p><b>Security:</b> Requires ADMIN role.
     *
     * <p><b>State Transition:</b> PENDING_APPROVAL → APPROVED
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

        log.info("Approve request for loader: {} by user: {}", loaderCode,
                authentication != null ? authentication.getName() : "UNKNOWN");

        String comments = request != null ? request.get("comments") : null;
        String ipAddress = null; // TODO: Extract from HttpServletRequest

        EtlLoaderDto approved = service.approveLoader(loaderCode, authentication, comments, ipAddress);

        log.info("Loader approved: {}", loaderCode);
        return ResponseEntity.ok(approved);
    }

    /**
     * Rejects a pending loader.
     *
     * <p><b>Security:</b> Requires ADMIN role.
     *
     * <p><b>State Transition:</b> PENDING_APPROVAL → REJECTED
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
     * @return Updated loader DTO
     */
    @PostMapping("/{loaderCode}/reject")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectLoader(
            @PathVariable String loaderCode,
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        log.info("Reject request for loader: {} by user: {}", loaderCode,
                authentication != null ? authentication.getName() : "UNKNOWN");

        String rejectionReason = request.get("rejectionReason");
        if (rejectionReason == null || rejectionReason.isBlank()) {
            log.warn("Rejection reason is required");
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "VALIDATION_REQUIRED_FIELD",
                    "message", "Rejection reason is required",
                    "field", "rejectionReason"
            ));
        }

        String comments = request.get("comments");
        String ipAddress = null; // TODO: Extract from HttpServletRequest

        EtlLoaderDto rejected = service.rejectLoader(loaderCode, rejectionReason, authentication, comments, ipAddress);

        log.info("Loader rejected: {}", loaderCode);
        return ResponseEntity.ok(rejected);
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

        // DEBUG: Remove after debugging
        log.info("DEBUG [LoaderController]: /test-query endpoint called");
        log.info("DEBUG [LoaderController]: Request received - sourceDatabaseId={}, sqlLength={}",
                request.getSourceDatabaseId(), request.getSql().length());

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

            // Check for dangerous keywords
            String[] dangerousKeywords = {"DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE", "ALTER", "CREATE"};
            for (String keyword : dangerousKeywords) {
                if (upperSql.contains(keyword)) {
                    return ResponseEntity.ok(TestQueryResponse.builder()
                            .success(false)
                            .message("Query contains write operation: " + keyword)
                            .errors(List.of("Loader queries must be read-only (SELECT only)",
                                    "Found forbidden keyword: " + keyword))
                            .build());
                }
            }

            // Add LIMIT 10 to prevent large result sets during testing
            String testSql = sql;
            if (!upperSql.contains("LIMIT")) {
                testSql = sql + " LIMIT 10";
                log.debug("Added LIMIT 10 to test query");
            }

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
}
