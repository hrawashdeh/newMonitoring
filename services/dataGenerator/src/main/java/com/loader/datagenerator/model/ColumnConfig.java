package com.loader.datagenerator.model;

import lombok.Data;

import java.util.List;

/**
 * Configuration for a single column in a table
 */
@Data
public class ColumnConfig {

    private String name;
    private ColumnType type;

    // For integer and float types
    private List<Number> range;  // [min, max]

    // For float type
    private Integer precision;

    // For list type
    private List<String> values;

    // For random_text type
    private Integer minLength;
    private Integer maxLength;

    public enum ColumnType {
        INTEGER,
        FLOAT,
        LIST,
        RANDOM_TEXT,
        TIMESTAMP
    }
}
