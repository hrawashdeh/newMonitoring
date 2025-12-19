package com.tiqmo.monitoring.loader.infra.db;

import com.tiqmo.monitoring.loader.dto.common.ErrorCode;
import com.tiqmo.monitoring.loader.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing queries against source databases.
 *
 * <p>Provides connection probing and query execution against
 * dynamically registered source databases.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceDbManager {

  private final SourceRegistry sources;

  /**
   * Probes a source database connection.
   *
   * @param sourceDbCode Source database code
   * @return true if connection successful, false otherwise
   */
  public boolean probe(String sourceDbCode) {
    MDC.put("sourceDbCode", sourceDbCode);

    try {
      log.debug("Probing source database | sourceDbCode={}", sourceDbCode);

      // Validation
      if (sourceDbCode == null || sourceDbCode.isBlank()) {
        log.warn("Source database code is null or blank");
        return false;
      }

      try (var c = sources.getConnection(sourceDbCode);
           var ps = c.prepareStatement("SELECT 1");
           var rs = ps.executeQuery()) {

        boolean success = rs.next();

        if (success) {
          log.info("Source database probe successful | sourceDbCode={}", sourceDbCode);
        } else {
          log.warn("Source database probe failed: no result | sourceDbCode={}", sourceDbCode);
        }

        return success;

      } catch (Exception e) {
        log.warn("Source database probe failed | sourceDbCode={} | error={}",
            sourceDbCode, e.getMessage());
        return false;
      }

    } finally {
      MDC.remove("sourceDbCode");
    }
  }

  /**
   * Executes a query against a source database.
   *
   * @param sourceDbCode Source database code
   * @param sql SQL query to execute
   * @return List of result rows as maps
   * @throws BusinessException if validation fails or query execution fails
   */
  public List<Map<String, Object>> runQuery(String sourceDbCode, String sql) {
    MDC.put("sourceDbCode", sourceDbCode);

    try {
      log.info("Executing query on source database | sourceDbCode={} | sqlLength={}",
          sourceDbCode, sql != null ? sql.length() : 0);

      // Validation
      if (sourceDbCode == null || sourceDbCode.isBlank()) {
        log.warn("Source database code is null or blank");
        throw new BusinessException(
            ErrorCode.VALIDATION_REQUIRED_FIELD,
            "Source database code is required",
            "sourceDbCode"
        );
      }

      if (sql == null || sql.isBlank()) {
        log.warn("SQL query is null or blank | sourceDbCode={}", sourceDbCode);
        throw new BusinessException(
            ErrorCode.VALIDATION_REQUIRED_FIELD,
            "SQL query is required",
            "sql"
        );
      }

      log.debug("Executing SQL | sourceDbCode={} | sql={}", sourceDbCode, sql);

      try (var conn = sources.getConnection(sourceDbCode);
           var ps = conn.prepareStatement(sql);
           var rs = ps.executeQuery()) {

        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();

        log.debug("Query metadata | sourceDbCode={} | columnCount={}", sourceDbCode, cols);

        while (rs.next()) {
          Map<String, Object> row = new LinkedHashMap<>(cols);
          for (int i = 1; i <= cols; i++) {
            row.put(md.getColumnLabel(i), rs.getObject(i));
          }
          rows.add(row);
        }

        log.info("Query executed successfully | sourceDbCode={} | rowCount={}",
            sourceDbCode, rows.size());

        return rows;

      } catch (Exception e) {
        log.error("Query execution failed | sourceDbCode={} | error={}",
            sourceDbCode, e.getMessage(), e);
        throw new BusinessException(
            ErrorCode.SOURCE_DATABASE_CONNECTION_FAILED,
            "Failed to execute query on source database '" + sourceDbCode + "': " + e.getMessage(),
            e
        );
      }

    } finally {
      MDC.remove("sourceDbCode");
    }
  }
}
