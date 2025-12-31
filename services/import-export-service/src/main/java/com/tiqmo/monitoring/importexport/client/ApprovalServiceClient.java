package com.tiqmo.monitoring.importexport.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tiqmo.monitoring.importexport.dto.LoaderImportDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Approval Service Client
 *
 * Calls loader-service approval API to create approval requests for loader updates.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalServiceClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${loader-service.base-url}")
    private String loaderServiceBaseUrl;

    /**
     * Submit approval request for loader UPDATE
     *
     * @param loader Loader data to be updated
     * @param importLabel Import batch label
     * @param requestedBy User requesting the change
     * @param token JWT token for authentication
     * @return Approval request response
     */
    @CircuitBreaker(name = "loader-service", fallbackMethod = "submitApprovalFallback")
    @Retry(name = "loader-service")
    public Mono<Map<String, Object>> submitLoaderUpdateApproval(
            LoaderImportDto loader,
            String importLabel,
            String requestedBy,
            String token) {

        // First, fetch the source database ID by code
        return webClientBuilder.baseUrl(loaderServiceBaseUrl)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/res/source-databases/by-code/{code}")
                        .build(loader.getSourceDatabaseCode()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(sourceDb -> {
                    try {
                        // Get source database ID
                        Long sourceDatabaseId = ((Number) sourceDb.get("id")).longValue();

                        // Convert LoaderImportDto to EtlLoaderDto format
                        ObjectNode loaderNode = objectMapper.createObjectNode();
                        loaderNode.put("loaderCode", loader.getLoaderCode());
                        loaderNode.put("loaderSql", loader.getLoaderSql());
                        loaderNode.put("sourceDatabaseId", sourceDatabaseId); // Use ID instead of code
                        loaderNode.put("minIntervalSeconds", loader.getMinIntervalSeconds());
                        loaderNode.put("maxIntervalSeconds", loader.getMaxIntervalSeconds());
                        loaderNode.put("maxQueryPeriodSeconds", loader.getMaxQueryPeriodSeconds());
                        loaderNode.put("maxParallelExecutions", loader.getMaxParallelExecutions());
                        loaderNode.put("purgeStrategy", loader.getPurgeStrategy());
                        loaderNode.put("sourceTimezoneOffsetHours", loader.getSourceTimezoneOffsetHours());
                        loaderNode.put("aggregationPeriodSeconds", loader.getAggregationPeriodSeconds());
                        loaderNode.put("enabled", false); // Default to false for safety

                        // Create approval request payload
                        Map<String, Object> requestPayload = new HashMap<>();
                        requestPayload.put("entityType", "LOADER");
                        requestPayload.put("entityId", loader.getLoaderCode());
                        requestPayload.put("requestType", "UPDATE");
                        requestPayload.put("requestData", loaderNode);
                        requestPayload.put("changeSummary", "Loader update from import: " + importLabel);
                        requestPayload.put("requestedBy", requestedBy);
                        requestPayload.put("source", "IMPORT");
                        requestPayload.put("importLabel", importLabel);

                        return webClientBuilder.baseUrl(loaderServiceBaseUrl)
                                .build()
                                .post()
                                .uri("/api/approvals/submit")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(requestPayload)
                                .retrieve()
                                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                                .doOnSuccess(response -> log.info("Approval request submitted for loader: {} (sourceDatabaseId: {})",
                                        loader.getLoaderCode(), sourceDatabaseId))
                                .doOnError(error -> log.error("Failed to submit approval request for loader: {}",
                                        loader.getLoaderCode(), error));

                    } catch (Exception e) {
                        log.error("Error preparing approval request for loader: {}", loader.getLoaderCode(), e);
                        return Mono.error(e);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Failed to fetch source database by code: {} for loader: {}",
                            loader.getSourceDatabaseCode(), loader.getLoaderCode(), error);
                    return Mono.error(new RuntimeException(
                            "Source database not found: " + loader.getSourceDatabaseCode(), error));
                });
    }

    /**
     * Fallback method for circuit breaker
     */
    private Mono<Map<String, Object>> submitApprovalFallback(
            LoaderImportDto loader,
            String importLabel,
            String requestedBy,
            String token,
            Throwable throwable) {

        log.error("Approval service fallback triggered for loader: {} - {}",
                loader.getLoaderCode(), throwable.getMessage());

        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("success", false);
        fallbackResponse.put("error", "Approval service unavailable: " + throwable.getMessage());
        return Mono.just(fallbackResponse);
    }
}
