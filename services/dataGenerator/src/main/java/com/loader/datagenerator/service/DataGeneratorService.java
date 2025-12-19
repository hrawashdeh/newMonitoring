package com.loader.datagenerator.service;

import com.loader.datagenerator.model.ColumnConfig;
import com.loader.datagenerator.model.DataGeneratorConfig;
import com.loader.datagenerator.model.DowntimeConfig;
import com.loader.datagenerator.model.TableConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/**
 * Service to generate random test data and insert via JDBC
 *
 * <p>DEV-ONLY: Generates random values based on column configuration
 * and inserts them into MySQL tables.</p>
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class DataGeneratorService {

    private final DataSource dataSource;
    private final DataGeneratorConfig config;
    private final Random random = new Random();

    private static final String RANDOM_TEXT_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 ";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Check if current time is within any configured downtime period
     */
    public boolean isDowntime() {
        if (config.getDowntime() == null || config.getDowntime().isEmpty()) {
            return false;
        }

        LocalTime now = LocalTime.now();

        for (DowntimeConfig downtime : config.getDowntime()) {
            LocalTime start = LocalTime.parse(downtime.getStart(), TIME_FORMATTER);
            LocalTime end = LocalTime.parse(downtime.getEnd(), TIME_FORMATTER);

            if (now.isAfter(start) && now.isBefore(end)) {
                log.info("Currently in downtime period: {} - {} ({})",
                        downtime.getStart(), downtime.getEnd(), downtime.getReason());
                return true;
            }
        }

        return false;
    }

    /**
     * Generate and insert records for all configured tables
     */
    public void generateAndInsertData() {
        if (isDowntime()) {
            log.info("Skipping data generation due to downtime period");
            return;
        }

        log.info("=== Starting data generation for {} tables ===", config.getTables().size());

        if (config.getTables().isEmpty()) {
            log.warn("WARNING: No tables configured for data generation!");
            return;
        }

        int totalRecords = 0;
        for (TableConfig table : config.getTables()) {
            try {
                generateForTable(table);
                totalRecords++;
            } catch (Exception e) {
                log.error("Failed to generate data for table: {}", table.getName(), e);
            }
        }

        log.info("=== Data generation completed for {} tables ===", totalRecords);
    }

    /**
     * Generate and insert random records for a single table
     */
    private void generateForTable(TableConfig table) throws SQLException {
        // Determine number of records to insert this minute
        int min = table.getRecordsPerMinute().getMin();
        int max = table.getRecordsPerMinute().getMax();
        int recordCount = min + random.nextInt(max - min + 1);

        log.info("Generating {} records for table: {} (min={}, max={})", recordCount, table.getName(), min, max);

        // Build INSERT statement
        String sql = buildInsertSql(table);
        log.debug("INSERT SQL: {}", sql);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < recordCount; i++) {
                setStatementParameters(stmt, table.getColumns());
                stmt.addBatch();

                // Log first record as sample
                if (i == 0) {
                    log.debug("Sample record for '{}': {}", table.getName(), stmt.toString());
                }
            }

            long startTime = System.currentTimeMillis();
            int[] results = stmt.executeBatch();
            long duration = System.currentTimeMillis() - startTime;

            log.info("✓ Successfully inserted {} records into '{}' in {}ms", results.length, table.getName(), duration);
        }
    }

    /**
     * Build INSERT SQL statement for a table
     */
    private String buildInsertSql(TableConfig table) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(table.getName()).append(" (");

        // Column names
        for (int i = 0; i < table.getColumns().size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(table.getColumns().get(i).getName());
        }

        sql.append(") VALUES (");

        // Placeholders
        for (int i = 0; i < table.getColumns().size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }

        sql.append(")");
        return sql.toString();
    }

    /**
     * Set prepared statement parameters with random values
     */
    private void setStatementParameters(PreparedStatement stmt, List<ColumnConfig> columns) throws SQLException {
        for (int i = 0; i < columns.size(); i++) {
            ColumnConfig column = columns.get(i);
            Object value = generateValue(column);

            int paramIndex = i + 1;

            switch (column.getType()) {
                case INTEGER:
                    stmt.setInt(paramIndex, (Integer) value);
                    break;
                case FLOAT:
                    stmt.setDouble(paramIndex, (Double) value);
                    break;
                case LIST:
                case RANDOM_TEXT:
                    stmt.setString(paramIndex, (String) value);
                    break;
                case TIMESTAMP:
                    stmt.setTimestamp(paramIndex, (Timestamp) value);
                    break;
            }
        }
    }

    /**
     * Generate a random value based on column configuration
     */
    private Object generateValue(ColumnConfig column) {
        switch (column.getType()) {
            case INTEGER:
                return generateInteger(column);
            case FLOAT:
                return generateFloat(column);
            case LIST:
                return generateFromList(column);
            case RANDOM_TEXT:
                return generateRandomText(column);
            case TIMESTAMP:
                return new Timestamp(System.currentTimeMillis());
            default:
                throw new IllegalArgumentException("Unsupported column type: " + column.getType());
        }
    }

    private Integer generateInteger(ColumnConfig column) {
        int min = column.getRange().get(0).intValue();
        int max = column.getRange().get(1).intValue();
        return min + random.nextInt(max - min + 1);
    }

    private Double generateFloat(ColumnConfig column) {
        double min = column.getRange().get(0).doubleValue();
        double max = column.getRange().get(1).doubleValue();
        double value = min + (max - min) * random.nextDouble();

        // Apply precision if specified
        if (column.getPrecision() != null) {
            double scale = Math.pow(10, column.getPrecision());
            value = Math.round(value * scale) / scale;
        }

        return value;
    }

    private String generateFromList(ColumnConfig column) {
        List<String> values = column.getValues();
        return values.get(random.nextInt(values.size()));
    }

    private String generateRandomText(ColumnConfig column) {
        int minLen = column.getMinLength();
        int maxLen = column.getMaxLength();
        int length = minLen + random.nextInt(maxLen - minLen + 1);

        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(RANDOM_TEXT_CHARS.charAt(random.nextInt(RANDOM_TEXT_CHARS.length())));
        }

        return text.toString().trim();
    }
}
