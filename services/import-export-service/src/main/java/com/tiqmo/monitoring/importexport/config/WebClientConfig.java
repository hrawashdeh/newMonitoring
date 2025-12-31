package com.tiqmo.monitoring.importexport.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient Configuration
 *
 * Configures WebClient for communication with loader-service.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final LoaderServiceProperties loaderServiceProperties;

    @Bean
    public WebClient loaderServiceWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) loaderServiceProperties.getTimeout().getConnect().toMillis())
                .responseTimeout(loaderServiceProperties.getTimeout().getRead())
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(
                                        loaderServiceProperties.getTimeout().getRead().toSeconds(),
                                        TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(
                                        loaderServiceProperties.getTimeout().getRead().toSeconds(),
                                        TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(loaderServiceProperties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}