package com.tiqmo.monitoring.loader.dto.loader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Loaders Overview Statistics
 * Operational metrics for loader management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadersStatsDto {

    /**
     * Total number of loaders
     */
    private Integer total;

    /**
     * Number of active (enabled) loaders
     */
    private Integer active;

    /**
     * Number of paused (disabled) loaders
     */
    private Integer paused;

    /**
     * Number of failed loaders (placeholder for future execution tracking)
     */
    private Integer failed;

    /**
     * Trend data (optional)
     */
    private TrendDto trend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDto {
        /**
         * Change in active loaders (e.g., "+8%")
         */
        private String activeChange;

        /**
         * Period for trend calculation (e.g., "24h")
         */
        private String period;
    }
}
