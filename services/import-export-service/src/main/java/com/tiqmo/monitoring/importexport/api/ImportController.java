package com.tiqmo.monitoring.importexport.api;

import com.tiqmo.monitoring.importexport.dto.ImportResultDto;
import com.tiqmo.monitoring.importexport.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Import Controller
 *
 * REST API endpoints for loader import operations.
 *
 * <p>Standardized Endpoint Pattern: /api/{service-id}/{controller-id}/{path}
 * <p>Service ID: imex (Import/Export Service)
 * <p>Controller ID: imp (Import Controller)
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@RestController
@RequestMapping("/api/imex/imp")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Import", description = "Loader import operations")
@SecurityRequirement(name = "bearer-jwt")
public class ImportController {

    private final ImportService importService;

    /**
     * Upload and import loaders from Excel file
     *
     * @param file Excel file (.xlsx)
     * @param importLabel User-provided label for this import batch
     * @param dryRun If true, validate only without creating loaders
     * @param authentication Spring Security authentication
     * @param request HTTP request
     * @return Import result with statistics and errors
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Upload and import loaders",
            description = "Upload Excel file and import loaders. Supports CREATE, UPDATE actions. " +
                    "Updates create draft versions for approval workflow."
    )
    public ResponseEntity<ImportResultDto> uploadImportFile(
            @Parameter(description = "Excel file (.xlsx)", required = true)
            @RequestPart("file") MultipartFile file,

            @Parameter(description = "Import label for traceability (e.g., '2024-12-Migration')")
            @RequestParam(required = false) String importLabel,

            @Parameter(description = "Dry run - validate only without processing")
            @RequestParam(defaultValue = "false") boolean dryRun,

            Authentication authentication,
            HttpServletRequest request) {

        String username = authentication.getName();
        String correlationId = MDC.get("correlationId");
        String requestPath = MDC.get("requestPath");
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();

        log.trace("Entering uploadImportFile() | username={} | fileName={} | fileSize={} | dryRun={} | correlationId={}",
                username, fileName, fileSize, dryRun, correlationId);

        log.debug("POST /api/imex/imp/upload | username={} | fileName={} | fileSize={} | importLabel={} | " +
                        "dryRun={} | correlationId={} | requestPath={}",
                username, fileName, fileSize, importLabel, dryRun, correlationId, requestPath);

        log.info("Import request received | username={} | fileName={} | fileSize={} | dryRun={} | importLabel={} | correlationId={}",
                username, fileName, fileSize, dryRun, importLabel, correlationId);

        // Extract JWT token from Authorization header
        log.trace("Extracting JWT token from Authorization header | correlationId={}", correlationId);
        String token = extractToken(request);
        if (token == null) {
            log.error("IMPORT_FAILED: Missing or invalid Authorization token | username={} | fileName={} | " +
                            "correlationId={} | statusCode=401 | " +
                            "reason=Authorization header missing or malformed | " +
                            "suggestion=Ensure Authorization header contains valid Bearer token",
                    username, fileName, correlationId);
            log.trace("Exiting uploadImportFile() | success=false | statusCode=401 | reason=missing_token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Validate file
        log.trace("Validating uploaded file | fileName={} | fileSize={} | isEmpty={} | correlationId={}",
                fileName, fileSize, file.isEmpty(), correlationId);

        if (file.isEmpty()) {
            log.error("IMPORT_FAILED: Empty file uploaded | username={} | fileName={} | correlationId={} | " +
                            "statusCode=400 | reason=File is empty | " +
                            "suggestion=Ensure file contains data before uploading",
                    username, fileName, correlationId);
            log.trace("Exiting uploadImportFile() | success=false | statusCode=400 | reason=empty_file");
            return ResponseEntity.badRequest().build();
        }

        if (!fileName.toLowerCase().endsWith(".xlsx")) {
            log.error("IMPORT_FAILED: Invalid file type | username={} | fileName={} | correlationId={} | " +
                            "statusCode=400 | reason=File must be Excel format (.xlsx) | " +
                            "suggestion=Upload a valid .xlsx file",
                    username, fileName, correlationId);
            log.trace("Exiting uploadImportFile() | success=false | statusCode=400 | reason=invalid_file_type");
            return ResponseEntity.badRequest().build();
        }

        log.debug("File validation passed | fileName={} | fileSize={} | correlationId={}", fileName, fileSize, correlationId);

        // Process import
        try {
            log.trace("Invoking import service | username={} | fileName={} | dryRun={} | correlationId={}",
                    username, fileName, dryRun, correlationId);

            ImportResultDto result = importService.importLoaders(
                    file,
                    importLabel,
                    username,
                    token,
                    dryRun
            );

            log.info("Import completed successfully | username={} | fileName={} | totalRows={} | successCount={} | " +
                            "failureCount={} | dryRun={} | auditLogId={} | correlationId={}",
                    username, fileName, result.getTotalRows(), result.getSuccessCount(),
                    result.getFailureCount(), dryRun, result.getAuditLogId(), correlationId);

            log.trace("Exiting uploadImportFile() | username={} | success=true | statusCode=200 | " +
                            "totalRows={} | successCount={}",
                    username, result.getTotalRows(), result.getSuccessCount());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("IMPORT_FAILED: Unexpected error during import | username={} | fileName={} | " +
                            "correlationId={} | errorType={} | errorMessage={} | " +
                            "reason=Unexpected error occurred during import processing | " +
                            "suggestion=Check file format and content, review error logs for details",
                    username, fileName, correlationId, e.getClass().getSimpleName(), e.getMessage(), e);

            log.trace("Exiting uploadImportFile() | username={} | success=false | statusCode=500 | reason=exception");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Validate Excel file without importing
     *
     * @param file Excel file (.xlsx)
     * @param authentication Spring Security authentication
     * @param request HTTP request
     * @return Validation result
     */
    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Validate import file",
            description = "Validate Excel file without actually importing loaders (dry run mode)"
    )
    public ResponseEntity<ImportResultDto> validateImportFile(
            @Parameter(description = "Excel file (.xlsx)", required = true)
            @RequestPart("file") MultipartFile file,

            Authentication authentication,
            HttpServletRequest request) {

        String username = authentication.getName();
        String correlationId = MDC.get("correlationId");
        String requestPath = MDC.get("requestPath");
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();

        log.trace("Entering validateImportFile() | username={} | fileName={} | fileSize={} | correlationId={}",
                username, fileName, fileSize, correlationId);

        log.debug("POST /api/imex/imp/validate | username={} | fileName={} | fileSize={} | " +
                        "correlationId={} | requestPath={}",
                username, fileName, fileSize, correlationId, requestPath);

        log.info("Validation request received | username={} | fileName={} | fileSize={} | correlationId={}",
                username, fileName, fileSize, correlationId);

        log.trace("Extracting JWT token from Authorization header | correlationId={}", correlationId);
        String token = extractToken(request);
        if (token == null) {
            log.error("VALIDATION_FAILED: Missing or invalid Authorization token | username={} | fileName={} | " +
                            "correlationId={} | statusCode=401 | " +
                            "reason=Authorization header missing or malformed | " +
                            "suggestion=Ensure Authorization header contains valid Bearer token",
                    username, fileName, correlationId);
            log.trace("Exiting validateImportFile() | success=false | statusCode=401 | reason=missing_token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            log.trace("Invoking import service in dry-run mode | username={} | fileName={} | correlationId={}",
                    username, fileName, correlationId);

            ImportResultDto result = importService.importLoaders(
                    file,
                    null,
                    username,
                    token,
                    true // Dry run
            );

            log.info("Validation completed successfully | username={} | fileName={} | totalRows={} | " +
                            "validationErrors={} | correlationId={}",
                    username, fileName, result.getTotalRows(), result.getErrors().size(), correlationId);

            log.trace("Exiting validateImportFile() | username={} | success=true | statusCode=200 | totalRows={}",
                    username, result.getTotalRows());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("VALIDATION_FAILED: Unexpected error during validation | username={} | fileName={} | " +
                            "correlationId={} | errorType={} | errorMessage={} | " +
                            "reason=Unexpected error occurred during file validation | " +
                            "suggestion=Check file format and content, review error logs for details",
                    username, fileName, correlationId, e.getClass().getSimpleName(), e.getMessage(), e);

            log.trace("Exiting validateImportFile() | username={} | success=false | statusCode=500 | reason=exception");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download import template
     *
     * @return Excel template file
     */
    @GetMapping("/template")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Download import template",
            description = "Download Excel template with all loader fields and ImportAction column"
    )
    public ResponseEntity<String> downloadTemplate() {
        // TODO: Implement template generation
        return ResponseEntity.ok("Template download endpoint - to be implemented");
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
