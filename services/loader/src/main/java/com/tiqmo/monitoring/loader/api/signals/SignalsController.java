// src/main/java/com/tiqmo/monitoring/loader/api/rest/SignalsController.java
package com.tiqmo.monitoring.loader.api.signals;

import com.tiqmo.monitoring.loader.domain.signals.entity.SignalsHistory;
import com.tiqmo.monitoring.loader.dto.signals.BulkSignalsRequest;
import com.tiqmo.monitoring.loader.service.signals.SignalsIngestService;
import com.tiqmo.monitoring.loader.service.signals.SignalsQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

        List<SignalsHistory> results;
        if (segmentCode != null && !segmentCode.isBlank()) {
            results = svc.byLoaderAndSegmentBetween(loaderCode, segmentCode, fromEpoch, toEpoch);
        } else {
            results = svc.byLoaderBetween(loaderCode, fromEpoch, toEpoch);
        }

        // Build response with reference keys at root level
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loaderCode", loaderCode);
        response.put("fromEpoch", fromEpoch);
        response.put("toEpoch", toEpoch);
        if (segmentCode != null && !segmentCode.isBlank()) {
            response.put("segmentCode", segmentCode);
        }
        response.put("signals", results);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<SignalsHistory> create(@Valid @RequestBody SignalsHistory signal) {
        // createdAt is now auto-managed by PostgreSQL DEFAULT NOW()
        SignalsHistory saved = ingestSvc.append(signal);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> createBulk(@Valid @RequestBody BulkSignalsRequest request) {
        List<SignalsHistory> saved = ingestSvc.bulkAppend(request.getLoaderCode(), request.getSignals());

        // Build response following JSON structure rules
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loaderCode", request.getLoaderCode());
        response.put("inserted", saved.size());
        response.put("signals", saved);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

