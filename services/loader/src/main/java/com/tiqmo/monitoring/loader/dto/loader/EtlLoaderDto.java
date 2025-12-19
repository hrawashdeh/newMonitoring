package com.tiqmo.monitoring.loader.dto.loader;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * ETL Loader Data Transfer Object with validation constraints.
 *
 * @author Hassan Rawashdeh
 * @since 2025-11-20
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtlLoaderDto {
    private Long id;

    @NotBlank(message = "Loader code is required")
    @Size(min = 1, max = 64, message = "Loader code must be between 1 and 64 characters")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Loader code must contain only uppercase letters, numbers, and underscores")
    private String loaderCode;

    @NotBlank(message = "Loader SQL is required")
    @Size(min = 10, max = 10000, message = "Loader SQL must be between 10 and 10000 characters")
    private String loaderSql;

    @NotNull(message = "Minimum interval is required")
    @Min(value = 1, message = "Minimum interval must be at least 1 second")
    @Max(value = 86400, message = "Minimum interval must not exceed 86400 seconds (24 hours)")
    private Integer minIntervalSeconds;

    @NotNull(message = "Maximum interval is required")
    @Min(value = 1, message = "Maximum interval must be at least 1 second")
    @Max(value = 86400, message = "Maximum interval must not exceed 86400 seconds (24 hours)")
    private Integer maxIntervalSeconds;

    @NotNull(message = "Maximum query period is required")
    @Min(value = 1, message = "Maximum query period must be at least 1 second")
    @Max(value = 604800, message = "Maximum query period must not exceed 604800 seconds (7 days)")
    private Integer maxQueryPeriodSeconds;

    @NotNull(message = "Maximum parallel executions is required")
    @Min(value = 1, message = "Maximum parallel executions must be at least 1")
    @Max(value = 100, message = "Maximum parallel executions must not exceed 100")
    private Integer maxParallelExecutions;

    private Boolean enabled;
}
