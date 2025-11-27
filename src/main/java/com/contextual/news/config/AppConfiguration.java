package com.contextual.news.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class AppConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public WebClient llmWebClient(AppProperties properties) {
        Duration timeout = properties.llm().getRequestTimeout();
        long timeoutMillis = timeout.toMillis();
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeoutMillis)
            .responseTimeout(timeout)
            .doOnConnected(connection -> connection
                .addHandlerLast(new ReadTimeoutHandler(timeoutMillis, TimeUnit.MILLISECONDS))
                .addHandlerLast(new WriteTimeoutHandler(timeoutMillis, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
            .baseUrl(properties.llm().getResolvedBaseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build())
            .build();
    }

    @Bean
    public CacheManager cacheManager(AppProperties properties) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
            new CaffeineCache("query-understanding", Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(Duration.ofHours(6))
                .maximumSize(1_000)
                .build()),
            new CaffeineCache("article-enrichment", Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(properties.enrichment().getCacheTtl())
                .maximumSize(5_000)
                .build()),
            new CaffeineCache("trending-feed", Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(2_000)
                .build())
        ));
        return cacheManager;
    }
}
