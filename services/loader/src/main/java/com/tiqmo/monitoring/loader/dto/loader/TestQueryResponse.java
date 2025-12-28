package com.tiqmo.monitoring.loader.dto.loader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for SQL query testing.
 * Contains execution results, errors, and sample data.
 *
 * @author Hassan Rawashdeh
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestQueryResponse {

    /**
     * Whether the query executed successfully.
     */
    private boolean success;

    /**
     * Human-readable message describing the result.
     */
    private String message;

    /**
     * Number of rows returned by the query (limited to first 10 rows for testing).
     */
    private Integer rowCount;

    /**
     * Total number of rows that would be returned (may be larger than rowCount if limited).
     */
    private Integer totalRowCount;

    /**
     * Query execution time in milliseconds.
     */
    private Long executionTimeMs;

    /**
     * Sample data from the query (first 10 rows).
     * Each row is a map of column name to value.
     */
    private List<Map<String, Object>> sampleData;

    /**
     * List of error messages if the query failed.
     */
    private List<String> errors;

    /**
     * List of warning messages (non-critical issues).
     */
    private List<String> warnings;
}
