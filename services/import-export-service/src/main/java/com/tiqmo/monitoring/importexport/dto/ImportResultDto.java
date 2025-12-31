package com.tiqmo.monitoring.importexport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Import Result DTO
 *
 * Response object returned after import operation.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDto {

    /**
     * Import audit log ID
     */
    private Long auditLogId;

    /**
     * Total rows in file
     */
    private Integer totalRows;

    /**
     * Number of successfully processed rows
     */
    private Integer successCount;

    /**
     * Number of failed rows
     */
    private Integer failureCount;

    /**
     * Import label (user-provided)
     */
    private String importLabel;

    /**
     * Was this a dry run?
     */
    private Boolean dryRun;

    /**
     * Validation/processing errors
     */
    @Builder.Default
    private List<ImportErrorDto> errors = new ArrayList<>();

    /**
     * Path to error file (if failures occurred)
     */
    private String errorFilePath;

    /**
     * Summary message
     */
    private String message;
}