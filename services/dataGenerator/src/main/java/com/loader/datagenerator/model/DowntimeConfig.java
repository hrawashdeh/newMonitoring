package com.loader.datagenerator.model;

import lombok.Data;

/**
 * Configuration for scheduled downtime periods (simulates system outages)
 */
@Data
public class DowntimeConfig {

    private String start;  // HH:mm format
    private String end;    // HH:mm format
    private String reason;
}
