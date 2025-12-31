package com.tiqmo.monitoring.importexport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Loader Import DTO
 *
 * Represents a single loader row from Excel import file.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoaderImportDto {

    // Import action
    private String importAction; // CREATE, UPDATE, DELETE

    // Loader identification
    private String loaderCode;

    // Loader configuration
    private String loaderSql;
    private Integer minIntervalSeconds;
    private Integer maxIntervalSeconds;
    private Integer maxQueryPeriodSeconds;
    private Integer maxParallelExecutions;
    private String purgeStrategy;
    private Integer sourceTimezoneOffsetHours;
    private Integer aggregationPeriodSeconds;
    private String sourceDatabaseCode; // Source database code (e.g., "TEST_MYSQL")

    // Row metadata
    private Integer rowNumber; // Excel row number for error reporting
}