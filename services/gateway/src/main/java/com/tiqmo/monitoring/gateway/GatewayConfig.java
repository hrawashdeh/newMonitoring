package com.tiqmo.monitoring.gateway;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Gateway configuration for custom filters and resolvers.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0 (Round 22)
 */
@Slf4j
@Configuration
public class GatewayConfig {

    /**
     * Key resolver for rate limiting (IP-based for Round 22).
     * Logs rate limiting decisions with full context for troubleshooting.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String correlationId = MDC.get("correlationId");
            String requestPath = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().name();

            String remoteAddr = Objects.requireNonNull(
                    exchange.getRequest().getRemoteAddress()
            ).getAddress().getHostAddress();

            log.trace("Rate limit key resolution | clientIp={} | requestPath={} | method={} | correlationId={}",
                    remoteAddr, requestPath, method, correlationId);

            log.debug("Rate limit key resolved: {} | requestPath={} | method={} | correlationId={}",
                    remoteAddr, requestPath, method, correlationId);

            return Mono.just(remoteAddr);
        };
    }
}
