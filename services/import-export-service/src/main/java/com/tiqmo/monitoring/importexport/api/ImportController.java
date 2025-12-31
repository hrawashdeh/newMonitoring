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
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@RestController
@RequestMapping("/api/import")
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

        log.info("Import request from user: {} (dryRun: {})", authentication.getName(), dryRun);

        // Extract JWT token from Authorization header
        String token = extractToken(request);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        if (!file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest().build();
        }

        // Process import
        try {
            ImportResultDto result = importService.importLoaders(
                    file,
                    importLabel,
                    authentication.getName(),
                    token,
                    dryRun
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Import failed", e);
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

        log.info("Validation request from user: {}", authentication.getName());

        String token = extractToken(request);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ImportResultDto result = importService.importLoaders(
                    file,
                    null,
                    authentication.getName(),
                    token,
                    true // Dry run
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Validation failed", e);
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
