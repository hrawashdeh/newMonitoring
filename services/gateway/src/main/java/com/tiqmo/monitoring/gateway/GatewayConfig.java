package com.tiqmo.monitoring.gateway;

import lombok.extern.slf4j.Slf4j;
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
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String remoteAddr = Objects.requireNonNull(
                    exchange.getRequest().getRemoteAddress()
            ).getAddress().getHostAddress();
            log.debug("Rate limit key resolved: {}", remoteAddr);
            return Mono.just(remoteAddr);
        };
    }
}
