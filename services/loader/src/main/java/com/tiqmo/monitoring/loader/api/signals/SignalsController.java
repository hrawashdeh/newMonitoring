// src/main/java/com/tiqmo/monitoring/loader/api/rest/SignalsController.java
package com.tiqmo.monitoring.loader.api.signals;

import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import com.tiqmo.monitoring.loader.dto.signals.BulkSignalsRequest;
import com.tiqmo.monitoring.loader.service.signals.SignalsIngestService;
import com.tiqmo.monitoring.loader.service.signals.SignalsQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service ID: ldr (Loader Service), Controller ID: sig (Signals Controller)
 */
@RestController
@RequestMapping("/api/ldr/sig")
@RequiredArgsConstructor
public class SignalsController {
    private final SignalsQueryService svc;
    private final SignalsIngestService ingestSvc;

    @GetMapping("/signal/{loaderCode}")
    public ResponseEntity<Map<String, Object>> byLoader(
            @PathVariable String loaderCode,
            @RequestParam long fromEpoch,
            @RequestParam long toEpoch,
            @RequestParam(required = false) String segmentCode) {

        log.trace("Entering byLoader() | loaderCode={} | fromEpoch={} | toEpoch={} | segmentCode={} | correlationId={} | requestPath={}",
                loaderCode, fromEpoch, toEpoch, segmentCode, MDC.get("correlationId"), MDC.get("requestPath"));
        log.debug("GET /signal/{} | timeRange=[{}, {}] | segmentCode={}", loaderCode, fromEpoch, toEpoch, segmentCode);

        List<SignalsHistory> results;
        if (segmentCode != null && !segmentCode.isBlank()) {
            log.debug("Querying signals with segment filter | loaderCode={} | segmentCode={}", loaderCode, segmentCode);
            results = svc.byLoaderAndSegmentBetween(loaderCode, segmentCode, fromEpoch, toEpoch);
        } else {
            log.debug("Querying signals without segment filter | loaderCode={}", loaderCode);
            results = svc.byLoaderBetween(loaderCode, fromEpoch, toEpoch);
        }

        log.info("Signals query successful | loaderCode={} | resultCount={} | correlationId={}",
                loaderCode, results.size(), MDC.get("correlationId"));

        // Build response with reference keys at root level
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loaderCode", loaderCode);
        response.put("fromEpoch", fromEpoch);
        response.put("toEpoch", toEpoch);
        if (segmentCode != null && !segmentCode.isBlank()) {
            response.put("segmentCode", segmentCode);
        }
        response.put("signals", results);

        log.trace("Exiting byLoader() | resultCount={} | statusCode=200", results.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<SignalsHistory> create(@Valid @RequestBody SignalsHistory signal) {
        log.trace("Entering create() | loaderCode={} | timestamp={} | correlationId={} | requestPath={}",
                signal.getLoaderCode(), signal.getLoadTimeStamp(), MDC.get("correlationId"), MDC.get("requestPath"));
        log.debug("POST /api/ldr/sig | loaderCode={}", signal.getLoaderCode());

        // createdAt is now auto-managed by PostgreSQL DEFAULT NOW()
        log.debug("Appending signal to database | loaderCode={}", signal.getLoaderCode());
        SignalsHistory saved = ingestSvc.append(signal);

        log.info("Signal created successfully | loaderCode={} | id={} | correlationId={}",
                signal.getLoaderCode(), saved.getId(), MDC.get("correlationId"));
        log.trace("Exiting create() | id={} | statusCode=201", saved.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> createBulk(@Valid @RequestBody BulkSignalsRequest request) {
        log.trace("Entering createBulk() | loaderCode={} | signalCount={} | correlationId={} | requestPath={}",
                request.getLoaderCode(), request.getSignals().size(), MDC.get("correlationId"), MDC.get("requestPath"));
        log.debug("POST /api/ldr/sig/bulk | loaderCode={} | signalCount={}", request.getLoaderCode(), request.getSignals().size());

        log.debug("Bulk appending signals | loaderCode={} | count={}", request.getLoaderCode(), request.getSignals().size());
        List<SignalsHistory> saved = ingestSvc.bulkAppend(request.getLoaderCode(), request.getSignals());

        log.info("Bulk signals created successfully | loaderCode={} | inserted={} | correlationId={}",
                request.getLoaderCode(), saved.size(), MDC.get("correlationId"));

        // Build response following JSON structure rules
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loaderCode", request.getLoaderCode());
        response.put("inserted", saved.size());
        response.put("signals", saved);

        log.trace("Exiting createBulk() | inserted={} | statusCode=201", saved.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

