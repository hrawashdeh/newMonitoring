package com.tiqmo.monitoring.loader.runner;

import com.tiqmo.monitoring.loader.events.SourcesLoadedEvent;
import com.tiqmo.monitoring.loader.infra.db.SourceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class SourceRegistryInitializer {

  private final SourceRegistry registry;
  private final ApplicationEventPublisher publisher;

  /** Optional dev-time periodic refresh - DISABLED (use manual reload via admin API instead). */
  // @Scheduled(fixedDelayString = "${app.sources.reload-ms:-1}")
  public void reloadOnSchedule() {
    // Method available for manual invocation but not scheduled
    registry.loadAll();
    publisher.publishEvent(new SourcesLoadedEvent(registry.getConfigs().keySet()));
    log.info("♻️ Reloaded source registry successfully");
  }
}
