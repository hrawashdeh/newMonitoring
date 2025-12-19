package com.tiqmo.monitoring.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway for ETL Monitoring Platform.
 *
 * <p>Provides centralized routing, rate limiting, circuit breaking, and security.
 *
 * <p><b>Round 22 Implementation</b>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0 (Round 22)
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
