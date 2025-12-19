package com.tiqmo.monitoring.loader.domain.config.entity;

/**
 * Data types for configuration values.
 * Used to parse string values from database into appropriate Java types.
 */
public enum ConfigDataType {
    /**
     * Integer value (e.g., "10" -> Integer.parseInt())
     */
    INTEGER,

    /**
     * Boolean value (e.g., "true" -> Boolean.parseBoolean())
     */
    BOOLEAN,

    /**
     * String value (used as-is)
     */
    STRING,

    /**
     * Double/floating point value (e.g., "1.5" -> Double.parseDouble())
     */
    DOUBLE,

    /**
     * Long integer value (e.g., "1000000" -> Long.parseLong())
     */
    LONG
}
