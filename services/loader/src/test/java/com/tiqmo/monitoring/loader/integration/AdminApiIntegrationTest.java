package com.tiqmo.monitoring.loader.integration;

import com.tiqmo.monitoring.loader.api.admin.LoaderAdminController;
import com.tiqmo.monitoring.loader.api.admin.LoaderAdminController.AdjustTimestampRequest;
import com.tiqmo.monitoring.loader.api.admin.LoaderAdminController.AdjustTimestampResponse;
import com.tiqmo.monitoring.loader.api.admin.LoaderAdminController.ExecutionHistoryResponse;
import com.tiqmo.monitoring.loader.api.admin.LoaderAdminController.LoaderStatusResponse;
import com.tiqmo.monitoring.loader.api.admin.LoaderAdminController.PauseResumeResponse;
import com.tiqmo.monitoring.loader.domain.loader.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Admin API endpoints.
 * <p>
 * Tests:
 * - GET /ops/v1/admin/loaders/{code}/status - Get loader status
 * - POST /ops/v1/admin/loaders/{code}/adjust-timestamp - Adjust lastLoadTimestamp
 * - POST /ops/v1/admin/loaders/{code}/pause - Pause loader
 * - POST /ops/v1/admin/loaders/{code}/resume - Resume loader
 * - GET /ops/v1/admin/loaders/history - Query execution history
 * <p>
 * Uses real database (H2 in-memory) to verify database operations.
 */
public class AdminApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LoaderAdminController adminController;

    // ============================================================================
    // GET /ops/v1/admin/loaders/{code}/status
    // ============================================================================

    @Test
    void getLoaderStatus_shouldReturnLoaderDetails() {
        // Given: A loader in database
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setLastLoadTimestamp(Instant.now().minus(5, ChronoUnit.MINUTES));
        loader.setLoadStatus(LoadStatus.IDLE);
        loaderRepository.save(loader);

        // When: Get loader status
        ResponseEntity<LoaderStatusResponse> response = adminController.getStatus("TEST_LOADER");

        // Then: Status returned successfully
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoaderStatusResponse status = response.getBody();
        assertThat(status).isNotNull();
        assertThat(status.loaderCode()).isEqualTo("TEST_LOADER");
        assertThat(status.enabled()).isTrue();
        assertThat(status.loadStatus()).isEqualTo("IDLE");
        assertThat(status.lastLoadTimestamp()).isNotNull();
        assertThat(status.minIntervalSeconds()).isEqualTo(10);
    }

    @Test
    void getLoaderStatus_shouldReturn404_whenLoaderNotFound() {
        // When: Get status for non-existent loader
        ResponseEntity<LoaderStatusResponse> response = adminController.getStatus("NONEXISTENT");

        // Then: 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ============================================================================
    // POST /ops/v1/admin/loaders/{code}/adjust-timestamp
    // ============================================================================

    @Test
    void adjustTimestamp_shouldUpdateLastLoadTimestamp() {
        // Given: A loader in database
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setLastLoadTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
        loaderRepository.save(loader);

        Instant newTimestamp = Instant.now().minus(30, ChronoUnit.MINUTES);
        AdjustTimestampRequest request = new AdjustTimestampRequest(newTimestamp);

        // When: Adjust timestamp
        ResponseEntity<AdjustTimestampResponse> response =
                adminController.adjustTimestamp("TEST_LOADER", request);

        // Then: Timestamp updated
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AdjustTimestampResponse result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.newTimestamp()).isEqualTo(newTimestamp);

        // Verify in database
        Loader updated = loaderRepository.findByLoaderCode("TEST_LOADER").orElseThrow();
        assertThat(updated.getLastLoadTimestamp()).isEqualTo(newTimestamp);
    }

    @Test
    void adjustTimestamp_shouldReturn404_whenLoaderNotFound() {
        // When: Adjust timestamp for non-existent loader
        AdjustTimestampRequest request = new AdjustTimestampRequest(Instant.now());
        ResponseEntity<AdjustTimestampResponse> response =
                adminController.adjustTimestamp("NONEXISTENT", request);

        // Then: 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ============================================================================
    // POST /ops/v1/admin/loaders/{code}/pause
    // ============================================================================

    @Test
    void pauseLoader_shouldSetEnabledToFalse() {
        // Given: An enabled loader
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setEnabled(true);
        loader.setLoadStatus(LoadStatus.IDLE);
        loaderRepository.save(loader);

        // When: Pause loader
        ResponseEntity<PauseResumeResponse> response = adminController.pauseLoader("TEST_LOADER");

        // Then: Loader paused
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PauseResumeResponse result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.loaderCode()).isEqualTo("TEST_LOADER");
        assertThat(result.newStatus()).isEqualTo("PAUSED");

        // Verify in database
        Loader updated = loaderRepository.findByLoaderCode("TEST_LOADER").orElseThrow();
        assertThat(updated.getLoadStatus()).isEqualTo(LoadStatus.PAUSED);
    }

    @Test
    void pauseLoader_shouldReturn404_whenLoaderNotFound() {
        // When: Pause non-existent loader
        ResponseEntity<PauseResumeResponse> response = adminController.pauseLoader("NONEXISTENT");

        // Then: 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ============================================================================
    // POST /ops/v1/admin/loaders/{code}/resume
    // ============================================================================

    @Test
    void resumeLoader_shouldSetEnabledToTrue() {
        // Given: A paused loader
        SourceDatabase sourceDb = sourceDatabaseRepository.save(createTestSourceDatabase("TEST_DB"));
        Loader loader = createTestLoader("TEST_LOADER", sourceDb);
        loader.setLoadStatus(LoadStatus.PAUSED);
        loaderRepository.save(loader);

        // When: Resume loader
        ResponseEntity<PauseResumeResponse> response = adminController.resumeLoader("TEST_LOADER");

        // Then: Loader resumed
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PauseResumeResponse result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.loaderCode()).isEqualTo("TEST_LOADER");
        assertThat(result.newStatus()).isEqualTo("IDLE");

        // Verify in database
        Loader updated = loaderRepository.findByLoaderCode("TEST_LOADER").orElseThrow();
        assertThat(updated.getLoadStatus()).isEqualTo(LoadStatus.IDLE);
    }

    @Test
    void resumeLoader_shouldReturn404_whenLoaderNotFound() {
        // When: Resume non-existent loader
        ResponseEntity<PauseResumeResponse> response = adminController.resumeLoader("NONEXISTENT");

        // Then: 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ============================================================================
    // GET /ops/v1/admin/loaders/history
    // ============================================================================

    @Test
    void queryExecutionHistory_shouldReturnAllHistory_whenNoFilters() {
        // Given: Multiple execution history records
        Instant now = Instant.now();
        loadHistoryRepository.save(createTestLoadHistory("LOADER_1", LoadExecutionStatus.SUCCESS,
                now.minus(5, ChronoUnit.MINUTES)));
        loadHistoryRepository.save(createTestLoadHistory("LOADER_1", LoadExecutionStatus.SUCCESS,
                now.minus(10, ChronoUnit.MINUTES)));
        loadHistoryRepository.save(createTestLoadHistory("LOADER_2", LoadExecutionStatus.FAILED,
                now.minus(15, ChronoUnit.MINUTES)));

        // When: Query all history
        ResponseEntity<List<ExecutionHistoryResponse>> response =
                adminController.queryExecutionHistory(null, null, null, null, null, 100);

        // Then: All records returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ExecutionHistoryResponse> history = response.getBody();
        assertThat(history).isNotNull();
        assertThat(history).hasSize(3);
    }

    @Test
    void queryExecutionHistory_shouldFilterByLoaderCode() {
        // Given: Multiple execution history records
        Instant now = Instant.now();
        loadHistoryRepository.save(createTestLoadHistory("LOADER_1", LoadExecutionStatus.SUCCESS,
                now.minus(5, ChronoUnit.MINUTES)));
        loadHistoryRepository.save(createTestLoadHistory("LOADER_1", LoadExecutionStatus.SUCCESS,
                now.minus(10, ChronoUnit.MINUTES)));
        loadHistoryRepository.save(createTestLoadHistory("LOADER_2", LoadExecutionStatus.FAILED,
                now.minus(15, ChronoUnit.MINUTES)));

        // When: Query by loader code
        ResponseEntity<List<ExecutionHistoryResponse>> response =
                adminController.queryExecutionHistory("LOADER_1", null, null, null, null, 100);

        // Then: Only LOADER_1 records returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ExecutionHistoryResponse> history = response.getBody();
        assertThat(history).isNotNull();
        assertThat(history).hasSize(2);
        assertThat(history).allMatch(h -> h.loaderCode().equals("LOADER_1"));
    }

    @Test
    void queryExecutionHistory_shouldFilterByStatus() {
        // Given: Multiple execution history records with different statuses
        Instant now = Instant.now();
        loadHistoryRepository.save(createTestLoadHistory("LOADER_1", LoadExecutionStatus.SUCCESS,
                now.minus(5, ChronoUnit.MINUTES)));
        loadHistoryRepository.save(createTestLoadHistory("LOADER_1", LoadExecutionStatus.FAILED,
                now.minus(10, ChronoUnit.MINUTES)));
        loadHistoryRepository.save(createTestLoadHistory("LOADER_2", LoadExecutionStatus.FAILED,
                now.minus(15, ChronoUnit.MINUTES)));

        // When: Query by status
        ResponseEntity<List<ExecutionHistoryResponse>> response =
                adminController.queryExecutionHistory(null, null, null, LoadExecutionStatus.FAILED,
                        null, 100);

        // Then: Only FAILED records returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ExecutionHistoryResponse> history = response.getBody();
        assertThat(history).isNotNull();
        assertThat(history).hasSize(2);
        assertThat(history).allMatch(h -> h.status().equals("FAILED"));
    }

    @Test
    void queryExecutionHistory_shouldRespectLimit() {
        // Given: 10 execution history records
        Instant now = Instant.now();
        for (int i = 0; i < 10; i++) {
            loadHistoryRepository.save(createTestLoadHistory("LOADER_1", LoadExecutionStatus.SUCCESS,
                    now.minus(i, ChronoUnit.MINUTES)));
        }

        // When: Query with limit=5
        ResponseEntity<List<ExecutionHistoryResponse>> response =
                adminController.queryExecutionHistory(null, null, null, null, null, 5);

        // Then: Only 5 records returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ExecutionHistoryResponse> history = response.getBody();
        assertThat(history).isNotNull();
        assertThat(history).hasSize(5);
    }

    @Test
    void queryExecutionHistory_shouldCapLimitAt1000() {
        // Given: Some execution history records
        Instant now = Instant.now();
        loadHistoryRepository.save(createTestLoadHistory("LOADER_1", LoadExecutionStatus.SUCCESS, now));

        // When: Query with limit > 1000
        ResponseEntity<List<ExecutionHistoryResponse>> response =
                adminController.queryExecutionHistory(null, null, null, null, null, 5000);

        // Then: Request succeeds (limit capped at 1000 internally)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void queryExecutionHistory_shouldFilterByTimeRange() {
        // Given: Execution history records across different time ranges
        Instant now = Instant.now();
        Instant twoHoursAgo = now.minus(2, ChronoUnit.HOURS);
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);

        loadHistoryRepository.save(createTestLoadHistory("LOADER_1", LoadExecutionStatus.SUCCESS,
                twoHoursAgo));  // Before range
        loadHistoryRepository.save(createTestLoadHistory("LOADER_1", LoadExecutionStatus.SUCCESS,
                oneHourAgo.plus(10, ChronoUnit.MINUTES)));  // In range
        loadHistoryRepository.save(createTestLoadHistory("LOADER_1", LoadExecutionStatus.SUCCESS,
                now.minus(5, ChronoUnit.MINUTES)));  // After range

        // When: Query with time range (1 hour ago to 30 minutes ago)
        Instant fromTime = oneHourAgo;
        Instant toTime = now.minus(30, ChronoUnit.MINUTES);
        ResponseEntity<List<ExecutionHistoryResponse>> response =
                adminController.queryExecutionHistory(null, fromTime, toTime, null, null, 100);

        // Then: Only records in range returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ExecutionHistoryResponse> history = response.getBody();
        assertThat(history).isNotNull();
        assertThat(history).hasSizeGreaterThanOrEqualTo(1);
        assertThat(history).allMatch(h ->
                h.startTime().isAfter(fromTime.minus(1, ChronoUnit.SECONDS)) &&
                        h.startTime().isBefore(toTime.plus(1, ChronoUnit.SECONDS))
        );
    }
}
