package com.tiqmo.monitoring.loader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // Round 10: Enable scheduler for LoaderSchedulerService
public class LoaderApplication {
	public static void main(String[] args) {
		SpringApplication.run(LoaderApplication.class, args);
	}
}