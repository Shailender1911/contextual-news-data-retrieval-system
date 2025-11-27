package com.contextual.news.service.trending;

import com.contextual.news.api.dto.TrendingEventRequest;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrendingEventSimulator {

    private static final Logger log = LoggerFactory.getLogger(TrendingEventSimulator.class);

    private final TrendingService trendingService;

    public TrendingEventSimulator(TrendingService trendingService) {
        this.trendingService = trendingService;
    }

    @Scheduled(fixedDelayString = "${app.trending.simulation-delay-ms:30000}")
    public void generateEvent() {
        try {
            if (ThreadLocalRandom.current().nextDouble() < 0.5) {
                // random chance to skip to reduce noise
                return;
            }
            TrendingEventRequest event = trendingService.randomEvent();
            trendingService.recordEvent(event);
            log.debug("Simulated trending event: {}", event);
        } catch (Exception ex) {
            log.trace("Failed to generate simulated trending event: {}", ex.getMessage());
        }
    }
}

