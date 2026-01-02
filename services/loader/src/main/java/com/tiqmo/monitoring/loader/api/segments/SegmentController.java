package com.tiqmo.monitoring.loader.api.segments;

import com.tiqmo.monitoring.loader.domain.loader.entity.SegmentDictionary;
import com.tiqmo.monitoring.loader.domain.signals.entity.SegmentCombination;
import com.tiqmo.monitoring.loader.service.segments.SegmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service ID: ldr (Loader Service), Controller ID: seg (Segments Controller)
 *
 * <p>REST Controller for segment management.
 *
 * <p>Provides endpoints to:
 * <ul>
 *   <li>Query segment dictionary (segment metadata/descriptions)</li>
 *   <li>Query segment combinations (actual segment values used in data)</li>
 * </ul>
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>GET /api/ldr/seg/dictionary?loaderCode={code} - Get segment dictionary for a loader</li>
 *   <li>GET /api/ldr/seg/combinations?loaderCode={code} - Get segment combinations for a loader</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/ldr/seg")
@RequiredArgsConstructor
@Slf4j
public class SegmentController {

    private final SegmentService service;

    /**
     * Gets segment dictionary for a loader.
     *
     * <p>Returns segment metadata including segment numbers, field names, and descriptions.
     * Used to understand what each segment (seg1-seg10) represents for a specific loader.
     *
     * <p><b>Example Request:</b>
     * <pre>
     * GET /api/v1/res/segments/dictionary?loaderCode=SALES_DAILY
     * </pre>
     *
     * <p><b>Example Response:</b>
     * <pre>
     * {
     *   "loaderCode": "SALES_DAILY",
     *   "segments": [
     *     {"segmentNumber": 1, "fieldName": "region", "description": "Sales region"},
     *     {"segmentNumber": 2, "fieldName": "product", "description": "Product category"}
     *   ]
     * }
     * </pre>
     *
     * @param loaderCode Loader code (required)
     * @return Segment dictionary for the loader
     * @throws IllegalArgumentException if loaderCode is null or blank (handled by GlobalExceptionHandler)
     */
    @GetMapping("/dictionary")
    public ResponseEntity<Map<String, Object>> getDictionary(@RequestParam String loaderCode) {
        log.info("Fetching segment dictionary for loader: {}", loaderCode);

        // Validate input
        if (loaderCode == null || loaderCode.isBlank()) {
            throw new IllegalArgumentException("Loader code is required");
        }

        List<SegmentDictionary> segments = service.findByLoader(loaderCode);

        log.info("Found {} segment definitions for loader: {}", segments.size(), loaderCode);

        // Build response with loaderCode at root level
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loaderCode", loaderCode);
        response.put("segments", segments);
        response.put("count", segments.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Gets segment combinations for a loader.
     *
     * <p>Returns all unique segment value combinations that exist in the signals_history data.
     * Each combination has a unique segment_code used for efficient querying.
     *
     * <p><b>Example Request:</b>
     * <pre>
     * GET /api/v1/res/segments/combinations?loaderCode=SALES_DAILY
     * </pre>
     *
     * <p><b>Example Response:</b>
     * <pre>
     * {
     *   "loaderCode": "SALES_DAILY",
     *   "combinations": [
     *     {"segmentCode": 1, "segment1": "NORTH", "segment2": "ELECTRONICS", "segment3": null, ...},
     *     {"segmentCode": 2, "segment1": "SOUTH", "segment2": "CLOTHING", "segment3": null, ...}
     *   ],
     *   "count": 2
     * }
     * </pre>
     *
     * @param loaderCode Loader code (required)
     * @return Segment combinations for the loader
     * @throws IllegalArgumentException if loaderCode is null or blank (handled by GlobalExceptionHandler)
     */
    @GetMapping("/combinations")
    public ResponseEntity<Map<String, Object>> getCombinations(@RequestParam String loaderCode) {
        log.info("Fetching segment combinations for loader: {}", loaderCode);

        // Validate input
        if (loaderCode == null || loaderCode.isBlank()) {
            throw new IllegalArgumentException("Loader code is required");
        }

        List<SegmentCombination> combinations = service.findCombinationsByLoader(loaderCode);

        log.info("Found {} segment combinations for loader: {}", combinations.size(), loaderCode);

        // Build response with loaderCode at root level
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loaderCode", loaderCode);
        response.put("combinations", combinations);
        response.put("count", combinations.size());

        return ResponseEntity.ok(response);
    }
}
