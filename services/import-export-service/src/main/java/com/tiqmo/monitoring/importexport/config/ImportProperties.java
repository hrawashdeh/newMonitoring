package com.tiqmo.monitoring.importexport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Import Configuration Properties
 *
 * Binds properties from application.yaml under 'import' prefix.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Component
@ConfigurationProperties(prefix = "import")
@Data
public class ImportProperties {

    /**
     * Maximum rows per import file
     */
    private Integer maxRowsPerFile = 10000;

    /**
     * Validation settings
     */
    private Validation validation = new Validation();

    @Data
    public static class Validation {
        /**
         * Required columns in import file
         */
        private List<String> requiredColumns = new ArrayList<>();
    }
}