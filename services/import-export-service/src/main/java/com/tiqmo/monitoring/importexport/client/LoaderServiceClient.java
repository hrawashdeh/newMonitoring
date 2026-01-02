package com.tiqmo.monitoring.importexport.client;

import com.tiqmo.monitoring.importexport.dto.LoaderImportDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
        String correlationId = MDC.get("correlationId");
        String loaderCode = loader.getLoaderCode();

        log.trace("Entering createLoader() | loaderCode={} | correlationId={}", loaderCode, correlationId);
        log.debug("Sending CREATE request to loader-service | loaderCode={} | uri=/api/v1/res/loaders | correlationId={}",
                loaderCode, correlationId);

        return loaderServiceWebClient.post()
                .uri("/api/v1/res/loaders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toCreateRequest(loader))
                .retrieve()
                .bodyToMono(LoaderResponse.class)
                .doOnSuccess(response -> {
                    log.info("Loader created successfully via loader-service | loaderCode={} | status={} | correlationId={}",
                            loaderCode, response.status(), correlationId);
                    log.trace("Exiting createLoader() | loaderCode={} | success=true", loaderCode);
                })
                .doOnError(error -> {
                    log.error("LOADER_SERVICE_ERROR: Failed to create loader | loaderCode={} | correlationId={} | " +
                                    "errorType={} | errorMessage={} | " +
                                    "reason=Loader service returned error during CREATE request",
                            loaderCode, correlationId, error.getClass().getSimpleName(), error.getMessage(), error);
                    log.trace("Exiting createLoader() | loaderCode={} | success=false | reason=service_error", loaderCode);
                });
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
        String correlationId = MDC.get("correlationId");

        log.trace("Entering loaderExists() | loaderCode={} | correlationId={}", loaderCode, correlationId);
        log.debug("Checking loader existence via loader-service | loaderCode={} | uri=/api/v1/res/loaders/{} | correlationId={}",
                loaderCode, loaderCode, correlationId);

        return loaderServiceWebClient.get()
                .uri("/api/v1/res/loaders/{loaderCode}", loaderCode)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(LoaderResponse.class)
                .map(response -> {
                    log.info("Loader exists check succeeded | loaderCode={} | exists=true | correlationId={}",
                            loaderCode, correlationId);
                    log.trace("Exiting loaderExists() | loaderCode={} | exists=true", loaderCode);
                    return true;
                })
                .onErrorResume(error -> {
                    log.debug("Loader does not exist or error occurred | loaderCode={} | errorType={} | correlationId={}",
                            loaderCode, error.getClass().getSimpleName(), correlationId);
                    log.trace("Exiting loaderExists() | loaderCode={} | exists=false", loaderCode);
                    return Mono.just(false);
                });
    }

    // ===== Fallback Methods =====

    private Mono<LoaderResponse> createLoaderFallback(LoaderImportDto loader, String token, Throwable t) {
        String correlationId = MDC.get("correlationId");
        String loaderCode = loader.getLoaderCode();

        log.error("CIRCUIT_BREAKER_OPEN: Create loader fallback triggered | loaderCode={} | correlationId={} | " +
                        "errorType={} | errorMessage={} | " +
                        "reason=Circuit breaker open or loader service unavailable | " +
                        "suggestion=Check loader service health and network connectivity",
                loaderCode, correlationId, t.getClass().getSimpleName(), t.getMessage(), t);

        return Mono.error(new RuntimeException("Loader service unavailable: " + t.getMessage()));
    }

    private Mono<LoaderResponse> updateLoaderFallback(LoaderImportDto loader, String token, Throwable t) {
        String correlationId = MDC.get("correlationId");
        String loaderCode = loader.getLoaderCode();

        log.error("CIRCUIT_BREAKER_OPEN: Update loader fallback triggered | loaderCode={} | correlationId={} | " +
                        "errorType={} | errorMessage={} | " +
                        "reason=Circuit breaker open or loader service unavailable | " +
                        "suggestion=Check loader service health and network connectivity",
                loaderCode, correlationId, t.getClass().getSimpleName(), t.getMessage(), t);

        return Mono.error(new RuntimeException("Loader service unavailable: " + t.getMessage()));
    }

    private Mono<Boolean> loaderExistsFallback(String loaderCode, String token, Throwable t) {
        String correlationId = MDC.get("correlationId");

        log.error("CIRCUIT_BREAKER_OPEN: Loader exists check fallback triggered | loaderCode={} | correlationId={} | " +
                        "errorType={} | errorMessage={} | " +
                        "reason=Circuit breaker open or loader service unavailable | " +
                        "action=Returning false as fallback",
                loaderCode, correlationId, t.getClass().getSimpleName(), t.getMessage(), t);

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