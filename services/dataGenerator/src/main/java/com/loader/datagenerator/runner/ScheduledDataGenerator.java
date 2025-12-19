package com.loader.datagenerator.runner;

import com.loader.datagenerator.service.DataGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled runner to generate test data every minute
 *
 * <p>DEV-ONLY: Runs on a fixed rate to simulate continuous data ingestion.</p>
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class ScheduledDataGenerator {

    private final DataGeneratorService dataGeneratorService;

    /**
     * Generate and insert data every minute
     */
    @Scheduled(fixedRate = 60000, initialDelay = 10000)  // Every 60 seconds, start after 10 seconds
    public void generateData() {
        log.info("Starting scheduled data generation...");
        try {
            dataGeneratorService.generateAndInsertData();
            log.info("Scheduled data generation completed successfully");
        } catch (Exception e) {
            log.error("Scheduled data generation failed", e);
        }
    }
}
