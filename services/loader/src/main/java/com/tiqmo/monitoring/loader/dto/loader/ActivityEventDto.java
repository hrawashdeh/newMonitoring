package com.tiqmo.monitoring.loader.dto.loader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for Recent Activity Events
 * Represents operational events in the system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEventDto {

    /**
     * Event timestamp (ISO 8601 format)
     */
    private String timestamp;

    /**
     * Event type
     */
    private String type;

    /**
     * Loader code associated with the event (optional)
     */
    private String loaderCode;

    /**
     * Human-readable event message
     */
    private String message;

    /**
     * Event status (success, error, warning, info)
     */
    private String status;

    /**
     * Create a simple activity event
     */
    public static ActivityEventDto of(String type, String loaderCode, String message, String status) {
        return ActivityEventDto.builder()
                .timestamp(Instant.now().toString())
                .type(type)
                .loaderCode(loaderCode)
                .message(message)
                .status(status)
                .build();
    }
}
