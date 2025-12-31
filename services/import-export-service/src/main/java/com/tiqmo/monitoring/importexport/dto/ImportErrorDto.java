package com.tiqmo.monitoring.importexport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Import Error DTO
 *
 * Represents a single validation or processing error during import.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportErrorDto {

    /**
     * Row number in Excel file (starting from 1)
     */
    private Integer row;

    /**
     * Loader code (if available)
     */
    private String loaderCode;

    /**
     * Field name where error occurred
     */
    private String field;

    /**
     * Error message
     */
    private String error;

    /**
     * Error type: VALIDATION, PROCESSING, SYSTEM
     */
    private String errorType;
}