package com.tiqmo.monitoring.loader.infra.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configuration for loader execution thread pool.
 *
 * <p>Creates a fixed thread pool for concurrent loader executions with timeout support.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ExecutorConfiguration {

  private final ExecutionProperties executionProperties;

  /**
   * Creates ExecutorService for loader executions.
   *
   * <p>Uses fixed thread pool with configurable size from {@code loader.execution.thread-pool-size}.
   * Threads are named "loader-exec-N" for easier debugging.
   *
   * @return configured ExecutorService
   */
  @Bean(name = "loaderExecutorService", destroyMethod = "shutdown")
  public ExecutorService loaderExecutorService() {
    int poolSize = executionProperties.getThreadPoolSize();

    log.info("Creating loader ExecutorService with pool size: {}", poolSize);

    ThreadFactory threadFactory = new ThreadFactory() {
      private final AtomicInteger threadNumber = new AtomicInteger(1);

      @Override
      public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, "loader-exec-" + threadNumber.getAndIncrement());
        thread.setDaemon(false); // Non-daemon threads for proper shutdown
        return thread;
      }
    };

    return Executors.newFixedThreadPool(poolSize, threadFactory);
  }
}
