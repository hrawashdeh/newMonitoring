package com.loader.datagenerator.runner;

import com.loader.datagenerator.model.ColumnConfig;
import com.loader.datagenerator.model.DataGeneratorConfig;
import com.loader.datagenerator.model.TableConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Creates tables in MySQL based on configuration on startup
 *
 * <p>DEV-ONLY: Automatically creates table schemas from YAML configuration.</p>
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class SchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final DataGeneratorConfig config;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== Initializing database schema ===");

        // Log JDBC connection details
        try (Connection conn = dataSource.getConnection()) {
            String jdbcUrl = conn.getMetaData().getURL();
            String username = conn.getMetaData().getUserName();
            String databaseProduct = conn.getMetaData().getDatabaseProductName();
            String databaseVersion = conn.getMetaData().getDatabaseProductVersion();

            log.info("JDBC URL: {}", jdbcUrl);
            log.info("Database User: {}", username);
            log.info("Database: {} {}", databaseProduct, databaseVersion);
        }

        log.info("Total tables to create: {}", config.getTables().size());

        if (config.getTables().isEmpty()) {
            log.warn("WARNING: No tables configured! Check data-generator.tables configuration.");
        }

        for (TableConfig table : config.getTables()) {
            createTableIfNotExists(table);
        }

        log.info("=== Database schema initialization completed ===");
    }

    private void createTableIfNotExists(TableConfig table) throws Exception {
        String createSql = buildCreateTableSql(table);

        log.info("Creating table '{}' with {} columns", table.getName(), table.getColumns().size());
        log.debug("SQL: {}", createSql);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createSql);
            log.info("✓ Table '{}' created successfully or already exists", table.getName());
        }
    }

    private String buildCreateTableSql(TableConfig table) {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        sql.append(table.getName()).append(" (\n");
        sql.append("  id BIGINT AUTO_INCREMENT PRIMARY KEY,\n");

        for (ColumnConfig column : table.getColumns()) {
            sql.append("  ").append(column.getName()).append(" ");

            switch (column.getType()) {
                case INTEGER:
                    sql.append("INT");
                    break;
                case FLOAT:
                    sql.append("DOUBLE");
                    break;
                case LIST:
                case RANDOM_TEXT:
                    sql.append("VARCHAR(255)");
                    break;
                case TIMESTAMP:
                    sql.append("TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                    break;
            }

            sql.append(",\n");
        }

        sql.append("  INDEX idx_timestamp (");
        // Find timestamp column and add index
        for (ColumnConfig column : table.getColumns()) {
            if (column.getType() == ColumnConfig.ColumnType.TIMESTAMP) {
                sql.append(column.getName());
                break;
            }
        }
        sql.append(")\n");

        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        return sql.toString();
    }
}
