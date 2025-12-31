package com.tiqmo.monitoring.importexport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * File Storage Configuration Properties
 *
 * Binds properties from application.yaml under 'file-storage' prefix.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Component
@ConfigurationProperties(prefix = "file-storage")
@Data
public class FileStorageProperties {

    /**
     * Base path for file storage (e.g., /app/imports)
     */
    private String basePath;

    /**
     * Path for error files (e.g., /app/imports/errors)
     */
    private String errorFilesPath;

    /**
     * Maximum file size in bytes (default: 10MB)
     */
    private Long maxFileSize = 10485760L;
}