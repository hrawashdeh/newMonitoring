package com.tiqmo.monitoring.importexport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Import-Export Service
 *
 * Microservice responsible for bulk import/export operations of loader configurations.
 * Handles Excel file parsing, validation, and communication with loader-service.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ImportExportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImportExportApplication.class, args);
    }
}