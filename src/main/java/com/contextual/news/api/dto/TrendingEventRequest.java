package com.contextual.news.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TrendingEventRequest(
    @NotNull
    EventType eventType,
    @NotNull
    UUID articleId,
    Location userLocation,
    OffsetDateTime occurredAt
) {

    public record Location(
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        double latitude,
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        double longitude
    ) {
    }

    public enum EventType {
        VIEW,
        CLICK,
        SHARE;

        public double weight() {
            return switch (this) {
                case VIEW -> 1.0;
                case CLICK -> 3.0;
                case SHARE -> 5.0;
            };
        }
    }
}

