package com.tiqmo.monitoring.importexport.client;

import com.tiqmo.monitoring.importexport.dto.LoaderImportDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Loader Service Client
 *
 * REST client for communication with loader-service.
 * Uses WebClient, Resilience4j circuit breaker, and retry patterns.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoaderServiceClient {

    private final WebClient loaderServiceWebClient;

    /**
     * Create a new loader
     *
     * @param loader Loader data
     * @param token JWT token
     * @return Created loader response
     */
    @CircuitBreaker(name = "loader-service", fallbackMethod = "createLoaderFallback")
    @Retry(name = "loader-service")
    public Mono<LoaderResponse> createLoader(LoaderImportDto loader, String token) {
        log.debug("Creating loader: {}", loader.getLoaderCode());

        return loaderServiceWebClient.post()
                .uri("/api/v1/res/loaders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toCreateRequest(loader))
                .retrieve()
                .bodyToMono(LoaderResponse.class)
                .doOnSuccess(response -> log.info("Created loader: {}", loader.getLoaderCode()))
                .doOnError(error -> log.error("Failed to create loader: {}", loader.getLoaderCode(), error));
    }

    /**
     * Update an existing loader (creates draft version for approval)
     *
     * @param loader Loader data
     * @param token JWT token
     * @return Updated loader response
     */
    @CircuitBreaker(name = "loader-service", fallbackMethod = "updateLoaderFallback")
    @Retry(name = "loader-service")
    public Mono<LoaderResponse> updateLoader(LoaderImportDto loader, String token) {
        log.debug("Updating loader: {}", loader.getLoaderCode());

        return loaderServiceWebClient.put()
                .uri("/api/v1/res/loaders/{loaderCode}", loader.getLoaderCode())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toUpdateRequest(loader))
                .retrieve()
                .bodyToMono(LoaderResponse.class)
                .doOnSuccess(response -> log.info("Updated loader: {}", loader.getLoaderCode()))
                .doOnError(error -> log.error("Failed to update loader: {}", loader.getLoaderCode(), error));
    }

    /**
     * Check if loader exists
     *
     * @param loaderCode Loader code
     * @param token JWT token
     * @return True if loader exists
     */
    @CircuitBreaker(name = "loader-service", fallbackMethod = "loaderExistsFallback")
    @Retry(name = "loader-service")
    public Mono<Boolean> loaderExists(String loaderCode, String token) {
        log.debug("Checking if loader exists: {}", loaderCode);

        return loaderServiceWebClient.get()
                .uri("/api/v1/res/loaders/{loaderCode}", loaderCode)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(LoaderResponse.class)
                .map(response -> true)
                .onErrorReturn(false);
    }

    // ===== Fallback Methods =====

    private Mono<LoaderResponse> createLoaderFallback(LoaderImportDto loader, String token, Throwable t) {
        log.error("Circuit breaker fallback: Failed to create loader {}", loader.getLoaderCode(), t);
        return Mono.error(new RuntimeException("Loader service unavailable: " + t.getMessage()));
    }

    private Mono<LoaderResponse> updateLoaderFallback(LoaderImportDto loader, String token, Throwable t) {
        log.error("Circuit breaker fallback: Failed to update loader {}", loader.getLoaderCode(), t);
        return Mono.error(new RuntimeException("Loader service unavailable: " + t.getMessage()));
    }

    private Mono<Boolean> loaderExistsFallback(String loaderCode, String token, Throwable t) {
        log.error("Circuit breaker fallback: Failed to check loader existence {}", loaderCode, t);
        return Mono.just(false);
    }

    // ===== Helper Methods =====

    private Object toCreateRequest(LoaderImportDto loader) {
        return new LoaderRequest(
                loader.getLoaderCode(),
                loader.getLoaderSql(),
                loader.getMinIntervalSeconds(),
                loader.getMaxIntervalSeconds(),
                loader.getMaxQueryPeriodSeconds(),
                loader.getMaxParallelExecutions(),
                loader.getPurgeStrategy(),
                loader.getSourceTimezoneOffsetHours(),
                loader.getAggregationPeriodSeconds(),
                loader.getSourceDatabaseCode()
        );
    }

    private Object toUpdateRequest(LoaderImportDto loader) {
        return new LoaderUpdateRequest(
                loader.getLoaderSql(),
                loader.getMinIntervalSeconds(),
                loader.getMaxIntervalSeconds(),
                loader.getMaxQueryPeriodSeconds(),
                loader.getMaxParallelExecutions(),
                loader.getPurgeStrategy(),
                loader.getSourceTimezoneOffsetHours(),
                loader.getAggregationPeriodSeconds(),
                loader.getSourceDatabaseCode()
        );
    }

    // ===== DTOs =====

    private record LoaderRequest(
            String loaderCode,
            String loaderSql,
            Integer minIntervalSeconds,
            Integer maxIntervalSeconds,
            Integer maxQueryPeriodSeconds,
            Integer maxParallelExecutions,
            String purgeStrategy,
            Integer sourceTimezoneOffsetHours,
            Integer aggregationPeriodSeconds,
            String sourceDatabaseCode
    ) {}

    private record LoaderUpdateRequest(
            String loaderSql,
            Integer minIntervalSeconds,
            Integer maxIntervalSeconds,
            Integer maxQueryPeriodSeconds,
            Integer maxParallelExecutions,
            String purgeStrategy,
            Integer sourceTimezoneOffsetHours,
            Integer aggregationPeriodSeconds,
            String sourceDatabaseCode
    ) {}

    public record LoaderResponse(
            String loaderCode,
            String status,
            String message
    ) {}
}