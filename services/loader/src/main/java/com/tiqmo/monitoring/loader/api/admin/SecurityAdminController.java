// src/main/java/com/tiqmo/monitoring/loader/controller/SecurityAdminController.java
package com.tiqmo.monitoring.loader.api.admin;

import com.tiqmo.monitoring.loader.probe.DbPermissionInspector;
import com.tiqmo.monitoring.loader.probe.PermissionReport;
import com.tiqmo.monitoring.loader.infra.db.SourceRegistry;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class SecurityAdminController {

  private final SourceRegistry registry;
  private final DbPermissionInspector inspector;

  public record ProbeView(String dbCode, boolean readOnly, List<String> violations) {}

  @GetMapping("/api/v1/admin/security/read-only-check")
  public List<ProbeView> checkAll() {
    List<ProbeView> list = new ArrayList<>();
    for (Map.Entry<String, HikariDataSource> e : registry.getPools().entrySet()) {
      PermissionReport r = inspector.inspect(e.getKey(), e.getValue());
      list.add(new ProbeView(r.getSourceCode(), r.isReadOnly(), r.getViolations()));
    }
    return list;
  }
}
