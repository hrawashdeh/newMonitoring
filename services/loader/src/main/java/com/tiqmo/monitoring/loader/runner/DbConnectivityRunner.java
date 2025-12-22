// src/main/java/com/tiqmo/monitoring/loader/runner/DbConnectivityRunner.java
package com.tiqmo.monitoring.loader.runner;

import com.tiqmo.monitoring.loader.events.SourcesLoadedEvent;
import com.tiqmo.monitoring.loader.probe.DbPermissionInspector;
import com.tiqmo.monitoring.loader.infra.db.SourceRegistry;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DbConnectivityRunner {

  private final SourceRegistry registry;
  private final DbPermissionInspector permissionInspector;
  private final Environment environment;
  boolean majorViolationDetected = false;

  /** Run only after SourceRegistry has loaded and announced pools. */
  @EventListener(SourcesLoadedEvent.class)
  public void runAfterSourcesLoaded(SourcesLoadedEvent evt) {
    log.info("== Dev probe@DbConnectivityRunner start ==");
    log.info("== Loaded source codes from event: {} = {}", evt.getDbCodes().size(), evt.getDbCodes());
    log.info("== Pools present in registry: {}", registry.getPools().size());

    for (Map.Entry<String, HikariDataSource> e : registry.getPools().entrySet()) {
      final String code = e.getKey();
      final HikariDataSource ds = e.getValue();
      log.info("== dev probe: {} | pool={}", code, ds);

      if (ds == null) {
        log.warn("Probe {}: FAILED (no pool)", code);
       // violationDetected = true;
        continue;
      }

      // 1) Connectivity
      try (Connection c = ds.getConnection()) {
        if (c.isValid(5)) {
          log.info("Probe {}: OK", code);
        } else {
          log.warn("Probe {}: FAILED (Connection#isValid=false)", code);
       //   violationDetected = true;
          continue;
        }
      } catch (Exception ex) {
        log.warn("Probe {}: FAILED ({})", code, ex.getMessage());
        continue;
      }

      // 2) Permission inspection (read-only vs. violations)
      var report = permissionInspector.inspect(code, ds);
      log.info("Permission report for {} ---", code);
      if (report.isReadOnly()) {
        log.info("🔒 {}: user appears READ-ONLY", code);
      } else {
        log.warn("⚠️ {}: user NOT read-only. Violations:", code);
        majorViolationDetected = true;
        for (String v : report.getViolations()) {
          log.error("   - {}", v);
        }
      }
    }

    if (majorViolationDetected) {
      // Check if running in dev profile
      boolean isDevProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");

      if (isDevProfile) {
        log.warn("⚠️ One or more source databases are NOT read-only — but continuing in DEV profile");
        log.warn("🔧 Dev Mode: Read-only validation disabled. Fix permissions for production!");
      } else {
        log.error("🚫 One or more source databases are NOT read-only — shutting down service!");
        log.info("== Dev probe@DbConnectivityRunner end ==");
        // Gracefully shut down the application context
        System.exit(1);
      }
    }
  }
}
