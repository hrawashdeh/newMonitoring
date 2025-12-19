package com.tiqmo.monitoring.loader.api.admin;

import com.tiqmo.monitoring.loader.domain.loader.entity.SourceDatabase;
import com.tiqmo.monitoring.loader.events.SourcesLoadedEvent;
import com.tiqmo.monitoring.loader.infra.db.SourceRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ops/v1/admin/")
@RequiredArgsConstructor
public class SourcesAdminController {

  private record SourceView(String dbCode, String host, Integer port, String dbName, SourceDatabase.DbType dbType) {}

  private final SourceRegistry registry;
  private final ApplicationEventPublisher publisher;

  /** List sources without secrets. */
  @GetMapping("res/db-sources")
  public List<SourceView> list() {
    return registry.getConfigs().values().stream()
            .map(this::toView)
            .toList();
  }

  /** Force a reload and publish an event (so probes/logs/consumers react). */
  @PostMapping("/security/reload")
  public void reload() {
    registry.loadAll();
    publisher.publishEvent(new SourcesLoadedEvent(registry.getConfigs().keySet()));
  }

  private SourceView toView(SourceDatabase s) {
    return new SourceView(
            s.getDbCode(),
            s.getIp(),
            s.getPort(),
            s.getDbName(),
            s.getDbType()
    );
  }
}
