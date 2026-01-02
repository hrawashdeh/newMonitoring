package com.tiqmo.monitoring.loader.service.segments;

import com.tiqmo.monitoring.loader.domain.loader.entity.SegmentDictionary;
import com.tiqmo.monitoring.loader.domain.loader.repo.SegmentDictionaryRepository;
import com.tiqmo.monitoring.loader.domain.signals.entity.SegmentCombination;
import com.tiqmo.monitoring.loader.domain.signals.repo.SegmentCombinationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing segments (dimensions for signals data).
 *
 * <p>Segments are multi-dimensional attributes used to categorize signals data.
 * Each loader can have up to 10 segments (seg1-seg10) for data slicing and filtering.
 *
 * <p><b>Segment Dictionary:</b> Defines what each segment number represents (metadata).
 * Example: seg1 = "region", seg2 = "product_category"
 *
 * <p><b>Segment Combinations:</b> Actual unique value combinations found in signals data.
 * Example: {seg1: "NORTH", seg2: "ELECTRONICS"} → segmentCode: 1
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SegmentService {

    private final SegmentDictionaryRepository repo;
    private final SegmentCombinationRepository combRepo;

    /**
     * Finds segment dictionary entries for a loader.
     *
     * <p>Returns metadata about what each segment (seg1-seg10) represents,
     * ordered by segment number.
     *
     * @param loaderCode Loader code
     * @return List of segment dictionary entries (sorted by segment number)
     */
    public List<SegmentDictionary> findByLoader(String loaderCode) {
        MDC.put("loaderCode", loaderCode);
        try {
            log.trace("Entering findByLoader() | loaderCode={} | correlationId={} | processId={}",
                    loaderCode, MDC.get("correlationId"), MDC.get("processId"));
            log.debug("Finding segment dictionary for loader: {}", loaderCode);

            log.trace("Querying segment dictionary repository | loaderCode={}", loaderCode);
            List<SegmentDictionary> segments = repo.findByLoader(loaderCode, Sort.by("segmentNumber"));

            log.debug("Found {} segment dictionary entries | loaderCode={}", segments.size(), loaderCode);
            log.trace("Exiting findByLoader() | resultCount={} | success=true", segments.size());

            return segments;
        } finally {
            MDC.remove("loaderCode");
        }
    }

    /**
     * Finds all segment combinations for a loader.
     *
     * <p>Returns all unique combinations of segment values that exist in
     * the signals_history table for this loader.
     *
     * @param loaderCode Loader code
     * @return List of segment combinations
     */
    public List<SegmentCombination> findCombinationsByLoader(String loaderCode) {
        MDC.put("loaderCode", loaderCode);
        try {
            log.trace("Entering findCombinationsByLoader() | loaderCode={} | correlationId={} | processId={}",
                    loaderCode, MDC.get("correlationId"), MDC.get("processId"));
            log.debug("Finding segment combinations for loader: {}", loaderCode);

            log.trace("Querying segment combination repository | loaderCode={}", loaderCode);
            List<SegmentCombination> combinations = combRepo.findAllByLoaderCode(loaderCode);

            log.debug("Found {} segment combinations | loaderCode={}", combinations.size(), loaderCode);
            log.trace("Exiting findCombinationsByLoader() | resultCount={} | success=true", combinations.size());

            return combinations;
        } finally {
            MDC.remove("loaderCode");
        }
    }
}
