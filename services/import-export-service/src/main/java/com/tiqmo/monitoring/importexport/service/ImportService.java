package com.tiqmo.monitoring.importexport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiqmo.monitoring.importexport.client.ApprovalServiceClient;
import com.tiqmo.monitoring.importexport.client.LoaderServiceClient;
import com.tiqmo.monitoring.importexport.domain.entity.ImportAuditLog;
import com.tiqmo.monitoring.importexport.domain.repo.ImportAuditLogRepository;
import com.tiqmo.monitoring.importexport.dto.ImportErrorDto;
import com.tiqmo.monitoring.importexport.dto.ImportResultDto;
import com.tiqmo.monitoring.importexport.dto.LoaderImportDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Import Service
 *
 * Main orchestration service for loader import operations.
 * Handles file upload, parsing, validation, and communication with loader-service.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private final ExcelParserService excelParserService;
    private final FileStorageService fileStorageService;
    private final LoaderServiceClient loaderServiceClient;
    private final ApprovalServiceClient approvalServiceClient;
    private final ImportAuditLogRepository importAuditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Import loaders from Excel file
     *
     * @param file Excel file
     * @param importLabel User-provided label for this import batch
     * @param username Username performing the import
     * @param token JWT token for loader-service authentication
     * @param dryRun If true, validate only without creating loaders
     * @return Import result with statistics and errors
     */
    @Transactional
    public ImportResultDto importLoaders(
            MultipartFile file,
            String importLabel,
            String username,
            String token,
            boolean dryRun) {

        String correlationId = MDC.get("correlationId");
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();

        log.trace("Entering importLoaders() | username={} | fileName={} | fileSize={} | importLabel={} | " +
                        "dryRun={} | correlationId={}",
                username, fileName, fileSize, importLabel, dryRun, correlationId);

        log.info("Starting import orchestration | username={} | fileName={} | fileSize={} | importLabel={} | " +
                        "dryRun={} | correlationId={}",
                username, fileName, fileSize, importLabel, dryRun, correlationId);

        List<ImportErrorDto> allErrors = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        String filePath = null;
        String errorFilePath = null;

        try {
            // Step 1: Store uploaded file
            log.trace("Step 1: Storing uploaded file | fileName={} | username={} | correlationId={}",
                    fileName, username, correlationId);
            filePath = fileStorageService.storeFile(file, username);
            log.info("File stored successfully | filePath={} | correlationId={}", filePath, correlationId);

            // Step 2: Parse Excel file
            log.trace("Step 2: Parsing Excel file | fileName={} | correlationId={}", fileName, correlationId);
            ExcelParserService.ParseResult parseResult = excelParserService.parseExcelFile(file);

            if (parseResult.hasErrors()) {
                log.warn("Parsing errors detected | errorCount={} | fileName={} | correlationId={}",
                        parseResult.errors().size(), fileName, correlationId);
                allErrors.addAll(parseResult.errors());
            } else {
                log.debug("File parsed without errors | fileName={} | correlationId={}", fileName, correlationId);
            }

            List<LoaderImportDto> loaders = parseResult.loaders();
            int totalRows = loaders.size();

            log.info("Excel file parsed successfully | fileName={} | totalRows={} | parsingErrors={} | correlationId={}",
                    fileName, totalRows, parseResult.errors().size(), correlationId);

            // Step 3: Validate and process each loader
            if (!dryRun && !parseResult.hasErrors()) {
                log.trace("Step 3: Processing loaders (not dry-run) | loaderCount={} | correlationId={}",
                        totalRows, correlationId);
                log.debug("Starting loader processing | loaderCount={} | importLabel={} | correlationId={}",
                        totalRows, importLabel, correlationId);

                ProcessResult processResult = processLoaders(loaders, importLabel, username, token)
                        .block(); // Block to wait for reactive processing

                if (processResult != null) {
                    successCount = processResult.successCount();
                    failureCount = processResult.failureCount();
                    allErrors.addAll(processResult.errors());

                    log.info("Loader processing completed | successCount={} | failureCount={} | " +
                                    "processingErrors={} | correlationId={}",
                            successCount, failureCount, processResult.errors().size(), correlationId);
                } else {
                    log.error("IMPORT_PROCESSING_FAILED: Process result is null | correlationId={} | " +
                                    "reason=Reactive processing returned null | " +
                                    "suggestion=Check reactive processing logic and error handling",
                            correlationId);
                }
            } else if (dryRun) {
                log.info("Dry run mode enabled - skipping loader processing | loaderCount={} | correlationId={}",
                        totalRows, correlationId);
                log.trace("Step 3: Skipping processing (dry-run mode) | correlationId={}", correlationId);
                successCount = loaders.size();
            } else {
                log.warn("Skipping loader processing due to parsing errors | parsingErrors={} | correlationId={}",
                        parseResult.errors().size(), correlationId);
            }

            // Step 4: Generate error file if failures occurred
            if (!allErrors.isEmpty()) {
                log.trace("Step 4: Generating error file | errorCount={} | correlationId={}",
                        allErrors.size(), correlationId);
                errorFilePath = fileStorageService.generateErrorFile(filePath, allErrors);
                log.info("Error file generated successfully | errorFilePath={} | errorCount={} | correlationId={}",
                        errorFilePath, allErrors.size(), correlationId);
            } else {
                log.trace("Step 4: Skipping error file generation (no errors) | correlationId={}", correlationId);
                log.debug("No errors to report - skipping error file generation | correlationId={}", correlationId);
            }

            // Step 5: Create audit log
            log.trace("Step 5: Creating import audit log | username={} | correlationId={}", username, correlationId);
            ImportAuditLog auditLog = createAuditLog(
                    file,
                    filePath,
                    importLabel,
                    username,
                    totalRows,
                    successCount,
                    failureCount,
                    allErrors,
                    dryRun
            );
            log.debug("Audit log created | auditLogId={} | correlationId={}", auditLog.getId(), correlationId);

            // Step 6: Build result
            log.trace("Step 6: Building import result | correlationId={}", correlationId);
            String message = buildResultMessage(successCount, failureCount, dryRun);

            ImportResultDto result = ImportResultDto.builder()
                    .auditLogId(auditLog.getId())
                    .totalRows(totalRows)
                    .successCount(successCount)
                    .failureCount(failureCount)
                    .importLabel(importLabel)
                    .dryRun(dryRun)
                    .errors(allErrors)
                    .errorFilePath(errorFilePath)
                    .message(message)
                    .build();

            log.info("Import orchestration completed successfully | totalRows={} | successCount={} | " +
                            "failureCount={} | dryRun={} | auditLogId={} | correlationId={}",
                    totalRows, successCount, failureCount, dryRun, auditLog.getId(), correlationId);

            log.trace("Exiting importLoaders() | success=true | successCount={} | failureCount={}",
                    successCount, failureCount);

            return result;

        } catch (IOException e) {
            log.error("IMPORT_FAILED: IO error during import | username={} | fileName={} | correlationId={} | " +
                            "errorType={} | errorMessage={} | " +
                            "reason=Failed to read or write file during import processing | " +
                            "suggestion=Check file permissions, disk space, and file format",
                    username, fileName, correlationId, e.getClass().getSimpleName(), e.getMessage(), e);
            log.trace("Exiting importLoaders() | success=false | reason=io_exception");
            throw new RuntimeException("Failed to process import file: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("IMPORT_FAILED: Unexpected error during import | username={} | fileName={} | correlationId={} | " +
                            "errorType={} | errorMessage={} | " +
                            "reason=Unexpected error occurred during import orchestration | " +
                            "suggestion=Review error logs for detailed stack trace and verify import process integrity",
                    username, fileName, correlationId, e.getClass().getSimpleName(), e.getMessage(), e);
            log.trace("Exiting importLoaders() | success=false | reason=unexpected_exception");
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process loaders (create or update)
     */
    private Mono<ProcessResult> processLoaders(
            List<LoaderImportDto> loaders,
            String importLabel,
            String username,
            String token) {

        List<ImportErrorDto> errors = new ArrayList<>();
        int[] successCount = {0};
        int[] failureCount = {0};

        return Flux.fromIterable(loaders)
                .flatMap(loader -> {
                    String action = determineAction(loader);

                    return switch (action) {
                        case "CREATE" -> processCreateLoader(loader, importLabel, username, token);
                        case "UPDATE" -> processUpdateLoader(loader, importLabel, username, token);
                        case "DELETE" -> Mono.just(new ProcessLoaderResult(false,
                                "DELETE action not yet implemented"));
                        default -> Mono.just(new ProcessLoaderResult(false,
                                "Invalid ImportAction: " + action));
                    };
                })
                .doOnNext(result -> {
                    if (result.success()) {
                        successCount[0]++;
                    } else {
                        failureCount[0]++;
                        errors.add(ImportErrorDto.builder()
                                .loaderCode(result.loaderCode())
                                .error(result.errorMessage())
                                .errorType("PROCESSING")
                                .build());
                    }
                })
                .then(Mono.just(new ProcessResult(successCount[0], failureCount[0], errors)));
    }

    /**
     * Process CREATE action
     * If loader already exists (409 Conflict), automatically try UPDATE instead
     */
    private Mono<ProcessLoaderResult> processCreateLoader(
            LoaderImportDto loader,
            String importLabel,
            String username,
            String token) {

        String correlationId = MDC.get("correlationId");
        String loaderCode = loader.getLoaderCode();

        log.trace("Entering processCreateLoader() | loaderCode={} | username={} | correlationId={}",
                loaderCode, username, correlationId);
        log.debug("Processing CREATE action | loaderCode={} | importLabel={} | correlationId={}",
                loaderCode, importLabel, correlationId);

        return loaderServiceClient.createLoader(loader, token)
                .map(response -> {
                    log.info("Loader created successfully | loaderCode={} | correlationId={}", loaderCode, correlationId);
                    log.trace("Exiting processCreateLoader() | loaderCode={} | success=true", loaderCode);
                    return new ProcessLoaderResult(
                            true,
                            loaderCode,
                            "Created successfully"
                    );
                })
                .onErrorResume(error -> {
                    // If 409 Conflict (loader already exists), try UPDATE instead
                    if (error.getMessage() != null && error.getMessage().contains("409")) {
                        log.warn("LOADER_CONFLICT: Loader already exists, switching to UPDATE | loaderCode={} | " +
                                        "correlationId={} | reason=409 Conflict from loader service | " +
                                        "action=Attempting UPDATE instead of CREATE",
                                loaderCode, correlationId);
                        // Skip existence check since we know it exists (got 409)
                        return submitApprovalRequest(loader, importLabel, username, token);
                    }

                    log.error("LOADER_CREATE_FAILED: Failed to create loader | loaderCode={} | correlationId={} | " +
                                    "errorType={} | errorMessage={} | " +
                                    "reason=Loader service returned error during creation",
                            loaderCode, correlationId, error.getClass().getSimpleName(), error.getMessage(), error);
                    log.trace("Exiting processCreateLoader() | loaderCode={} | success=false | reason=create_error", loaderCode);
                    return Mono.just(new ProcessLoaderResult(
                            false,
                            loaderCode,
                            "Create failed: " + error.getMessage()
                    ));
                });
    }

    /**
     * Process UPDATE action
     * Submits approval request via approval workflow
     */
    private Mono<ProcessLoaderResult> processUpdateLoader(
            LoaderImportDto loader,
            String importLabel,
            String username,
            String token) {

        String correlationId = MDC.get("correlationId");
        String loaderCode = loader.getLoaderCode();

        log.trace("Entering processUpdateLoader() | loaderCode={} | username={} | correlationId={}",
                loaderCode, username, correlationId);
        log.debug("Processing UPDATE action | loaderCode={} | importLabel={} | correlationId={}",
                loaderCode, importLabel, correlationId);

        log.trace("Checking if loader exists | loaderCode={} | correlationId={}", loaderCode, correlationId);

        return loaderServiceClient.loaderExists(loaderCode, token)
                .flatMap(exists -> {
                    if (!exists) {
                        log.error("LOADER_UPDATE_FAILED: Loader does not exist | loaderCode={} | correlationId={} | " +
                                        "reason=Cannot UPDATE non-existent loader | " +
                                        "suggestion=Use CREATE action or verify loader code is correct",
                                loaderCode, correlationId);
                        log.trace("Exiting processUpdateLoader() | loaderCode={} | success=false | reason=not_found", loaderCode);
                        return Mono.just(new ProcessLoaderResult(
                                false,
                                loaderCode,
                                "Loader does not exist - use CREATE action"
                        ));
                    }

                    log.debug("Loader exists, proceeding with approval request | loaderCode={} | correlationId={}",
                            loaderCode, correlationId);

                    // Loader exists, submit approval request
                    return submitApprovalRequest(loader, importLabel, username, token);
                });
    }

    /**
     * Submit approval request for loader update
     * Called when we know the loader exists (either from existence check or 409 Conflict)
     */
    private Mono<ProcessLoaderResult> submitApprovalRequest(
            LoaderImportDto loader,
            String importLabel,
            String username,
            String token) {

        String correlationId = MDC.get("correlationId");
        String loaderCode = loader.getLoaderCode();

        log.trace("Entering submitApprovalRequest() | loaderCode={} | importLabel={} | username={} | correlationId={}",
                loaderCode, importLabel, username, correlationId);
        log.debug("Submitting approval request | loaderCode={} | importLabel={} | correlationId={}",
                loaderCode, importLabel, correlationId);

        return approvalServiceClient.submitLoaderUpdateApproval(
                        loader, importLabel, username, token)
                .map(response -> {
                    // Check if approval request was successful
                    boolean success = response.get("id") != null;
                    if (success) {
                        Long approvalId = (Long) response.get("id");
                        log.info("Approval request submitted successfully | loaderCode={} | approvalId={} | " +
                                        "importLabel={} | correlationId={}",
                                loaderCode, approvalId, importLabel, correlationId);
                        log.trace("Exiting submitApprovalRequest() | loaderCode={} | success=true | approvalId={}",
                                loaderCode, approvalId);
                        return new ProcessLoaderResult(
                                true,
                                loaderCode,
                                "Approval request submitted (pending approval)"
                        );
                    } else {
                        String error = (String) response.getOrDefault("error",
                                "Unknown error submitting approval");
                        log.error("APPROVAL_SUBMISSION_FAILED: Approval service returned error | loaderCode={} | " +
                                        "correlationId={} | error={} | " +
                                        "reason=Approval service response indicates failure",
                                loaderCode, correlationId, error);
                        log.trace("Exiting submitApprovalRequest() | loaderCode={} | success=false | reason=service_error",
                                loaderCode);
                        return new ProcessLoaderResult(
                                false,
                                loaderCode,
                                error
                        );
                    }
                })
                .onErrorResume(error -> {
                    log.error("APPROVAL_SUBMISSION_FAILED: Failed to submit approval request | loaderCode={} | " +
                                    "correlationId={} | errorType={} | errorMessage={} | " +
                                    "reason=Error communicating with approval service",
                            loaderCode, correlationId, error.getClass().getSimpleName(), error.getMessage(), error);
                    log.trace("Exiting submitApprovalRequest() | loaderCode={} | success=false | reason=exception", loaderCode);
                    return Mono.just(new ProcessLoaderResult(
                            false,
                            loaderCode,
                            "Failed to submit approval: " + error.getMessage()
                    ));
                });
    }

    /**
     * Determine action (CREATE, UPDATE, DELETE)
     */
    private String determineAction(LoaderImportDto loader) {
        String action = loader.getImportAction();
        if (action == null || action.trim().isEmpty()) {
            return "UPDATE"; // Default action
        }
        return action.trim().toUpperCase();
    }

    /**
     * Create import audit log
     */
    private ImportAuditLog createAuditLog(
            MultipartFile file,
            String filePath,
            String importLabel,
            String username,
            int totalRows,
            int successCount,
            int failureCount,
            List<ImportErrorDto> errors,
            boolean dryRun) {

        String validationErrorsJson = null;
        if (!errors.isEmpty()) {
            try {
                validationErrorsJson = objectMapper.writeValueAsString(errors);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize validation errors", e);
            }
        }

        ImportAuditLog auditLog = ImportAuditLog.builder()
                .fileName(file.getOriginalFilename())
                .filePath(filePath)
                .fileSizeBytes(file.getSize())
                .importLabel(importLabel)
                .importedBy(username)
                .importedAt(LocalDateTime.now())
                .totalRows(totalRows)
                .successCount(successCount)
                .failureCount(failureCount)
                .validationErrors(validationErrorsJson)
                .dryRun(dryRun)
                .build();

        return importAuditLogRepository.save(auditLog);
    }

    /**
     * Build result message
     */
    private String buildResultMessage(int successCount, int failureCount, boolean dryRun) {
        if (dryRun) {
            return String.format("Dry run completed: %d loaders validated", successCount);
        }

        if (failureCount == 0) {
            return String.format("Import successful: %d loaders processed", successCount);
        }

        return String.format("Import completed with errors: %d succeeded, %d failed",
                successCount, failureCount);
    }

    // ===== Helper Records =====

    private record ProcessResult(
            int successCount,
            int failureCount,
            List<ImportErrorDto> errors
    ) {}

    private record ProcessLoaderResult(
            boolean success,
            String loaderCode,
            String errorMessage
    ) {
        ProcessLoaderResult(boolean success, String errorMessage) {
            this(success, null, errorMessage);
        }
    }
}