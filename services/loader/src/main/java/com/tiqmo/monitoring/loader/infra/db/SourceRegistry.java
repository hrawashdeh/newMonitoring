package com.tiqmo.monitoring.loader.infra.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.tiqmo.monitoring.loader.domain.loader.entity.SourceDatabase;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SourceRegistry {

  @PersistenceContext
  private EntityManager em;


  private final ApplicationEventPublisher publisher;
  private final com.tiqmo.monitoring.loader.infra.config.SourcePoolProperties poolProperties;

  private final Map<String, SourceDatabase> configByCode = new ConcurrentHashMap<>();
  private final Map<String, HikariDataSource> poolsByCode = new ConcurrentHashMap<>();

  public SourceRegistry(ApplicationEventPublisher publisher,
                        com.tiqmo.monitoring.loader.infra.config.SourcePoolProperties poolProperties) {
    this.publisher = publisher;
    this.poolProperties = poolProperties;
  }


  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    log.info("🔄 ApplicationReadyEvent – loading source databases...");
    loadAll();
    var loadedCodes = new java.util.HashSet<>(poolsByCode.keySet());
    publisher.publishEvent(new com.tiqmo.monitoring.loader.events.SourcesLoadedEvent(loadedCodes));
  }

  @PreDestroy
  public void shutdown() {
    closeAll();
  }

  /** Immutable snapshot of configs. */
  public Map<String, SourceDatabase> getConfigs() {
    return Collections.unmodifiableMap(new HashMap<>(configByCode));
  }

  /** Direct access to the pool (nullable). */
  public HikariDataSource getPool(String dbCode) {
    return poolsByCode.get(dbCode);
  }

  /** Borrow a JDBC connection from the pool for the given code. */
  public Connection getConnection(String dbCode) throws Exception {
    HikariDataSource ds = poolsByCode.get(dbCode);
    if (ds == null) {
      throw new IllegalArgumentException("Unknown dbCode: " + dbCode);
    }
    return ds.getConnection(); // caller closes
  }

  /** Simple probe: get a connection and run SELECT 1. */
  public boolean probe(String dbCode) {
    HikariDataSource ds = poolsByCode.get(dbCode);
    if (ds == null) return false;

    try (Connection c = ds.getConnection();
         Statement st = c.createStatement();
         ResultSet rs = st.executeQuery("SELECT 1")) {
      return rs.next();
    } catch (Exception e) {
      log.warn("Probe failed for {}: {}", dbCode, e.getMessage());
      return false;
    }
  }

  /** Reload all configs from DB and rebuild pools. */
  public synchronized void loadAll() {
    List<SourceDatabase> rows = em
            .createQuery("select s from SourceDatabase s", SourceDatabase.class)
            .getResultList();

    if (rows.isEmpty()) {
      log.warn("⚠️  No source databases found in loader.source_databases");
      replaceState(Map.of(), Map.of());
      return;
    }

    Map<String, SourceDatabase> nextConfigs = new HashMap<>();
    Map<String, HikariDataSource> nextPools = new HashMap<>();

    for (SourceDatabase cfg : rows) {
      String code = cfg.getDbCode();
      if (code == null || code.isBlank()) {
        log.warn("Skipping source with empty db_code");
        continue;
      }
      nextConfigs.put(code, cfg);
      HikariDataSource ds = buildPool(cfg);
      nextPools.put(code, ds);
      log.info("✅ Initialized pool for source {}", code);
    }

    replaceState(nextConfigs, nextPools);
    log.info("✅ Loaded {} source database configs", nextConfigs.size());
  }

  private HikariDataSource buildPool(SourceDatabase cfg) {
    String code = cfg.getDbCode();
    String type = (cfg.getDbType() == null) ? "" : cfg.getDbType().toString().toUpperCase(Locale.ROOT);

    String jdbcUrl;
    if ("MYSQL".equals(type)) {
      jdbcUrl = "jdbc:mysql://" + cfg.getIp() + ":" + cfg.getPort() + "/" + cfg.getDbName()
              + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    } else if ("POSTGRESQL".equals(type) || "POSTGRES".equals(type) || "PG".equals(type)) {
      jdbcUrl = "jdbc:postgresql://" + cfg.getIp() + ":" + cfg.getPort() + "/" + cfg.getDbName();
    } else {
      throw new IllegalArgumentException("Unsupported db_type for " + code + ": " + cfg.getDbType());
    }

    HikariConfig hc = new HikariConfig();
    hc.setPoolName("src-" + code);
    hc.setJdbcUrl(jdbcUrl);
    hc.setUsername(cfg.getUserName());
    hc.setPassword(cfg.getPassWord());

    if ("MYSQL".equals(type)) hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
    if ("POSTGRESQL".equals(type) || "POSTGRES".equals(type) || "PG".equals(type))
      hc.setDriverClassName("org.postgresql.Driver");

    // FIXED Issue #15: Use configurable pool properties instead of hardcoded values
    // Defaults from SourcePoolProperties: maximumPoolSize=5, minimumIdle=1, idleTimeout=5min
    hc.setMaximumPoolSize(poolProperties.getMaximumPoolSize());
    hc.setMinimumIdle(poolProperties.getMinimumIdle());
    hc.setIdleTimeout(poolProperties.getIdleTimeoutMs());
    hc.setConnectionTimeout(poolProperties.getConnectionTimeoutMs());

    if (poolProperties.getLeakDetectionThresholdMs() > 0) {
      hc.setLeakDetectionThreshold(poolProperties.getLeakDetectionThresholdMs());
    }

    hc.setInitializationFailTimeout(5_000); // Keep fast fail on startup
    hc.setConnectionTestQuery("SELECT 1");

    log.debug("Created HikariCP pool for '{}': maxPoolSize={}, minIdle={}, idleTimeout={}ms, connTimeout={}ms",
        code,
        poolProperties.getMaximumPoolSize(),
        poolProperties.getMinimumIdle(),
        poolProperties.getIdleTimeoutMs(),
        poolProperties.getConnectionTimeoutMs());

    return new HikariDataSource(hc);
  }

  private void replaceState(Map<String, SourceDatabase> nextConfigs,
                            Map<String, HikariDataSource> nextPools) {
    // Close pools that are going away
    Set<String> toClose = new HashSet<>(poolsByCode.keySet());
    toClose.removeAll(nextPools.keySet());
    for (String code : toClose) {
      HikariDataSource old = poolsByCode.remove(code);
      if (old != null) {
        try { old.close(); log.info("Closed old pool {}", code); }
        catch (Exception e) { log.warn("Error closing pool {}: {}", code, e.getMessage()); }
      }
    }

    // Replace configs
    configByCode.clear();
    configByCode.putAll(nextConfigs);

    // Close all existing pools and install the new set (simple & safe)
    for (HikariDataSource ds : poolsByCode.values()) {
      try { ds.close(); } catch (Exception ignore) {}
    }
    poolsByCode.clear();
    poolsByCode.putAll(nextPools);
  }

  // in SourceRegistry
  public Map<String, HikariDataSource> getPools() { return Collections.unmodifiableMap(poolsByCode); }


  private void closeAll() {
    for (HikariDataSource ds : poolsByCode.values()) {
      try { ds.close(); } catch (Exception ignore) {}
    }
    poolsByCode.clear();
  }
}
