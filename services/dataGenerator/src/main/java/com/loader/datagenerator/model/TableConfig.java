package com.loader.datagenerator.model;

import lombok.Data;

import java.util.List;

/**
 * Configuration for a single table to generate data for
 */
@Data
public class TableConfig {

    private String name;
    private RecordRateConfig recordsPerMinute;
    private List<ColumnConfig> columns;

    @Data
    public static class RecordRateConfig {
        private Integer min;
        private Integer max;
    }
}
