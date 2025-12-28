package com.tiqmo.monitoring.loader.dto.loader;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for testing SQL queries against a source database.
 * Used to validate SQL syntax and execution before creating/updating a loader.
 *
 * @author Hassan Rawashdeh
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestQueryRequest {

    /**
     * Source database ID to test the query against.
     */
    @NotNull(message = "Source database ID is required")
    private Long sourceDatabaseId;

    /**
     * SQL query to test.
     * Must be a SELECT query (read-only).
     */
    @NotBlank(message = "SQL query is required")
    @Size(min = 10, max = 10000, message = "SQL query must be between 10 and 10,000 characters")
    private String sql;
}
