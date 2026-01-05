package com.tiqmo.monitoring.loader.api.admin;

import com.tiqmo.monitoring.loader.domain.loader.entity.BackfillJob;
import com.tiqmo.monitoring.loader.domain.loader.entity.BackfillJobStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.PurgeStrategy;
import com.tiqmo.monitoring.loader.dto.admin.BackfillJobResponse;
import com.tiqmo.monitoring.loader.dto.admin.SubmitBackfillRequest;
import com.tiqmo.monitoring.loader.infra.config.ApiKey;
import com.tiqmo.monitoring.loader.service.backfill.BackfillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service ID: ldr (Loader Service), Controller ID: bkfl (Backfill Controller)
 *
 * <p>Admin Controller for managing backfill jobs.
 *
 * <p>Provides endpoints to:
 * <ul>
 *   <li>Submit backfill jobs</li>
 *   <li>Execute backfill jobs</li>
 *   <li>Query job status</li>
 *   <li>Cancel pending jobs</li>
 * </ul>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/ldr/bkfl/submit - Submit a new backfill job</li>
 *   <li>POST /api/ldr/bkfl/{id}/execute - Execute a pending job</li>
 *   <li>GET /api/ldr/bkfl/{id} - Get job by ID</li>
 *   <li>GET /api/ldr/bkfl/loader/{loaderCode} - Get jobs by loader</li>
 *   <li>GET /api/ldr/bkfl/recent - Get recent jobs</li>
 *   <li>POST /api/ldr/bkfl/{id}/cancel - Cancel a pending job</li>
 *   <li>GET /api/ldr/bkfl/stats - Get backfill statistics</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/ldr/bkfl")
@RequiredArgsConstructor
@Slf4j
public class BackfillAdminController {

    private final BackfillService backfillService;

    /**
     * Submit a new backfill job.
     *
     * <p>Example request:
     * <pre>
     * POST /ops/v1/admin/backfill/submit
     * {
     *   "loaderCode": "SALES_DAILY",
     *   "fromTimeEpoch": 1704067200,
     *   "toTimeEpoch": 1704153600,
     *   "purgeStrategy": "PURGE_AND_RELOAD",
     *   "requestedBy": "admin@example.com"
     * }
     * </pre>
     *
     * @param request Backfill request
     * @return Created backfill job
     */
    @PostMapping("/submit")
    @ApiKey(value = "ldr.backfill.submit", description = "Submit a new backfill job", tags = {"admin"})
    public ResponseEntity<BackfillJobResponse> submitBackfillJob(
            @Valid @RequestBody SubmitBackfillRequest request) {

        log.info("Received backfill request for loader {} (range: {} to {}, strategy: {})",
            request.getLoaderCode(), request.getFromTimeEpoch(), request.getToTimeEpoch(),
            request.getPurgeStrategy());

        // Parse purge strategy
        PurgeStrategy purgeStrategy = request.getPurgeStrategy() != null
            ? PurgeStrategy.valueOf(request.getPurgeStrategy())
            : PurgeStrategy.PURGE_AND_RELOAD;

        // Submit job
        BackfillJob job = backfillService.submitBackfillJob(
            request.getLoaderCode(),
            Instant.ofEpochSecond(request.getFromTimeEpoch()),
            Instant.ofEpochSecond(request.getToTimeEpoch()),
            purgeStrategy,
            request.getRequestedBy() != null ? request.getRequestedBy() : "api"
        );

        return ResponseEntity.ok(BackfillJobResponse.fromEntity(job));
    }

    /**
     * Execute a backfill job.
     *
     * <p>Note: This is a synchronous operation. For long-running backfills,
     * consider using an async executor or background processor.
     *
     * @param id Job ID
     * @return Executed backfill job
     */
    @PostMapping("/{id}/execute")
    @ApiKey(value = "ldr.backfill.execute", description = "Execute a pending backfill job", tags = {"admin"})
    public ResponseEntity<BackfillJobResponse> executeBackfillJob(@PathVariable Long id) {
        log.info("Executing backfill job #{}", id);

        BackfillJob job = backfillService.executeBackfillJob(id);

        return ResponseEntity.ok(BackfillJobResponse.fromEntity(job));
    }

    /**
     * Get backfill job by ID.
     *
     * @param id Job ID
     * @return Backfill job details
     */
    @GetMapping("/{id}")
    @ApiKey(value = "ldr.backfill.get", description = "Get backfill job by ID", tags = {"admin"})
    public ResponseEntity<BackfillJobResponse> getBackfillJob(@PathVariable Long id) {
        BackfillJob job = backfillService.getBackfillJob(id)
            .orElseThrow(() -> new IllegalArgumentException("Backfill job not found: " + id));

        return ResponseEntity.ok(BackfillJobResponse.fromEntity(job));
    }

    /**
     * Get backfill jobs for a loader.
     *
     * @param loaderCode Loader code
     * @return List of backfill jobs (most recent first)
     */
    @GetMapping("/loader/{loaderCode}")
    @ApiKey(value = "ldr.backfill.byLoader", description = "Get backfill jobs by loader code", tags = {"admin"})
    public ResponseEntity<List<BackfillJobResponse>> getBackfillJobsByLoader(
            @PathVariable String loaderCode) {

        List<BackfillJob> jobs = backfillService.getBackfillJobsByLoader(loaderCode);

        List<BackfillJobResponse> responses = jobs.stream()
            .map(BackfillJobResponse::fromEntity)
            .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Get recent backfill jobs.
     *
     * @param limit Max results (default: 50, max: 500)
     * @return Recent backfill jobs (most recent first)
     */
    @GetMapping("/recent")
    @ApiKey(value = "ldr.backfill.recent", description = "Get recent backfill jobs", tags = {"admin"})
    public ResponseEntity<List<BackfillJobResponse>> getRecentBackfillJobs(
            @RequestParam(defaultValue = "50") int limit) {

        // Cap limit at 500
        if (limit > 500) {
            limit = 500;
        }

        List<BackfillJob> jobs = backfillService.getRecentBackfillJobs(limit);

        List<BackfillJobResponse> responses = jobs.stream()
            .map(BackfillJobResponse::fromEntity)
            .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Cancel a pending backfill job.
     *
     * @param id Job ID
     * @return Cancelled job
     */
    @PostMapping("/{id}/cancel")
    @ApiKey(value = "ldr.backfill.cancel", description = "Cancel a pending backfill job", tags = {"admin"})
    public ResponseEntity<BackfillJobResponse> cancelBackfillJob(@PathVariable Long id) {
        log.info("Cancelling backfill job #{}", id);

        BackfillJob job = backfillService.cancelBackfillJob(id);

        return ResponseEntity.ok(BackfillJobResponse.fromEntity(job));
    }

    /**
     * Get backfill statistics.
     *
     * @return Statistics about active and recent backfill jobs
     */
    @GetMapping("/stats")
    @ApiKey(value = "ldr.backfill.stats", description = "Get backfill statistics", tags = {"admin"})
    public ResponseEntity<Map<String, Object>> getBackfillStats() {
        long activeJobs = backfillService.countActiveJobs();

        List<BackfillJob> recentJobs = backfillService.getRecentBackfillJobs(100);

        long totalJobs = recentJobs.size();
        long successfulJobs = recentJobs.stream()
            .filter(j -> j.getStatus() == BackfillJobStatus.SUCCESS)
            .count();
        long failedJobs = recentJobs.stream()
            .filter(j -> j.getStatus() == BackfillJobStatus.FAILED)
            .count();
        long cancelledJobs = recentJobs.stream()
            .filter(j -> j.getStatus() == BackfillJobStatus.CANCELLED)
            .count();

        return ResponseEntity.ok(Map.of(
            "activeJobs", activeJobs,
            "recentJobsCount", totalJobs,
            "successfulJobs", successfulJobs,
            "failedJobs", failedJobs,
            "cancelledJobs", cancelledJobs,
            "timestamp", Instant.now().getEpochSecond()
        ));
    }
}
