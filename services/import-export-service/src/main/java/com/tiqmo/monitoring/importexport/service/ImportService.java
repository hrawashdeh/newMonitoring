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

        log.info("Starting import for user: {} (dryRun: {})", username, dryRun);

        List<ImportErrorDto> allErrors = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        String filePath = null;
        String errorFilePath = null;

        try {
            // Step 1: Store uploaded file
            filePath = fileStorageService.storeFile(file, username);
            log.info("Stored file: {}", filePath);

            // Step 2: Parse Excel file
            ExcelParserService.ParseResult parseResult = excelParserService.parseExcelFile(file);

            if (parseResult.hasErrors()) {
                log.warn("Parsing errors found: {}", parseResult.errors().size());
                allErrors.addAll(parseResult.errors());
            }

            List<LoaderImportDto> loaders = parseResult.loaders();
            int totalRows = loaders.size();

            log.info("Parsed {} loaders from file", totalRows);

            // Step 3: Validate and process each loader
            if (!dryRun && !parseResult.hasErrors()) {
                ProcessResult processResult = processLoaders(loaders, importLabel, username, token)
                        .block(); // Block to wait for reactive processing

                if (processResult != null) {
                    successCount = processResult.successCount();
                    failureCount = processResult.failureCount();
                    allErrors.addAll(processResult.errors());
                }
            } else if (dryRun) {
                log.info("Dry run mode - skipping loader processing");
                successCount = loaders.size();
            }

            // Step 4: Generate error file if failures occurred
            if (!allErrors.isEmpty()) {
                errorFilePath = fileStorageService.generateErrorFile(filePath, allErrors);
                log.info("Generated error file: {}", errorFilePath);
            }

            // Step 5: Create audit log
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

            // Step 6: Build result
            String message = buildResultMessage(successCount, failureCount, dryRun);

            return ImportResultDto.builder()
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

        } catch (IOException e) {
            log.error("IO error during import", e);
            throw new RuntimeException("Failed to process import file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during import", e);
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

        return loaderServiceClient.createLoader(loader, token)
                .map(response -> new ProcessLoaderResult(
                        true,
                        loader.getLoaderCode(),
                        "Created successfully"
                ))
                .onErrorResume(error -> {
                    // If 409 Conflict (loader already exists), try UPDATE instead
                    if (error.getMessage() != null && error.getMessage().contains("409")) {
                        log.info("Loader {} already exists, switching to UPDATE", loader.getLoaderCode());
                        // Skip existence check since we know it exists (got 409)
                        return submitApprovalRequest(loader, importLabel, username, token);
                    }

                    log.error("Failed to create loader: {}", loader.getLoaderCode(), error);
                    return Mono.just(new ProcessLoaderResult(
                            false,
                            loader.getLoaderCode(),
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

        return loaderServiceClient.loaderExists(loader.getLoaderCode(), token)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.just(new ProcessLoaderResult(
                                false,
                                loader.getLoaderCode(),
                                "Loader does not exist - use CREATE action"
                        ));
                    }

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

        return approvalServiceClient.submitLoaderUpdateApproval(
                        loader, importLabel, username, token)
                .map(response -> {
                    // Check if approval request was successful
                    boolean success = response.get("id") != null;
                    if (success) {
                        return new ProcessLoaderResult(
                                true,
                                loader.getLoaderCode(),
                                "Approval request submitted (pending approval)"
                        );
                    } else {
                        String error = (String) response.getOrDefault("error",
                                "Unknown error submitting approval");
                        return new ProcessLoaderResult(
                                false,
                                loader.getLoaderCode(),
                                error
                        );
                    }
                })
                .onErrorResume(error -> {
                    log.error("Failed to submit approval request: {}",
                            loader.getLoaderCode(), error);
                    return Mono.just(new ProcessLoaderResult(
                            false,
                            loader.getLoaderCode(),
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