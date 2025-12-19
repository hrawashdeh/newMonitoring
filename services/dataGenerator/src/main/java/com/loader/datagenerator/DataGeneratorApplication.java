package com.loader.datagenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Test Data Generator Application
 *
 * <p>DEV-ONLY: Generates random test data and inserts into MySQL database
 * to simulate real production data for testing the ETL loader service.</p>
 *
 * <p>Features:
 * - Configurable table schemas via YAML
 * - Random data generation (integers, floats, lists, text)
 * - Configurable record insertion rate (records per minute)
 * - Downtime simulation periods
 * - JDBC-based insertion</p>
 *
 * <p>Only runs with Spring profile: dev</p>
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@Profile("dev")  // DEV-ONLY - will not start in production
public class DataGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataGeneratorApplication.class, args);
    }
}
