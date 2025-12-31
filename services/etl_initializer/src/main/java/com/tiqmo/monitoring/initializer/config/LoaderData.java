package com.tiqmo.monitoring.initializer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "etl")
public class LoaderData {
    private Metadata metadata;
    private List<SourceDatabaseConfig> sources;
    private List<LoaderConfig> loaders;

    @Data
    public static class Metadata {
        private Integer loadVersion;        // Version being loaded
        private String description;         // Description of this version
    }

    @Data
    public static class SourceDatabaseConfig {
        private Integer version;            // Version when this source was added
        private String dbCode;
        private String ip;
        private Integer port;
        private String dbType;
        private String dbName;
        private String userName;
        private String passWord;  // Plain text in YAML, encrypted when saved to DB
    }

    @Data
    public static class LoaderConfig {
        private Integer version;            // Version when this loader was added
        private String loaderCode;
        private String loaderSql;  // Plain text in YAML, encrypted when saved to DB
        private String sourceDbCode;
        private Integer minIntervalSeconds;
        private Integer maxIntervalSeconds;
        private Integer maxQueryPeriodSeconds;
        private Integer maxParallelExecutions;
        private String loadStatus;
        private String purgeStrategy;
        private Integer consecutiveZeroRecordRuns;
        private Boolean enabled;
        private Integer aggregationPeriodSeconds;
        private Integer sourceTimezoneOffsetHours;
    }
}
