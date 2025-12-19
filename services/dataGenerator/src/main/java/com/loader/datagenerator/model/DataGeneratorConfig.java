package com.loader.datagenerator.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Root configuration for test data generator
 */
@Data
@Component
@ConfigurationProperties(prefix = "data-generator")
public class DataGeneratorConfig {

    private MysqlConfig mysql;
    private List<TableConfig> tables;
    private List<DowntimeConfig> downtime;

    @Data
    public static class MysqlConfig {
        private String host;
        private Integer port;
        private String database;
        private String user;
        private String password;
    }
}
