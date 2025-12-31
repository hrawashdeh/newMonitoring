package com.tiqmo.monitoring.importexport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Loader Service Configuration Properties
 *
 * Binds properties from application.yaml under 'loader-service' prefix.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Component
@ConfigurationProperties(prefix = "loader-service")
@Data
public class LoaderServiceProperties {

    /**
     * Base URL of loader service (e.g., http://signal-loader:8080)
     */
    private String baseUrl;

    /**
     * Timeout settings
     */
    private Timeout timeout = new Timeout();

    /**
     * Retry settings
     */
    private Retry retry = new Retry();

    @Data
    public static class Timeout {
        private Duration connect = Duration.ofSeconds(5);
        private Duration read = Duration.ofSeconds(30);
    }

    @Data
    public static class Retry {
        private Integer maxAttempts = 3;
        private Duration backoffDelay = Duration.ofSeconds(1);
    }
}