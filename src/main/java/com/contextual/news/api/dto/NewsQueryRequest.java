package com.contextual.news.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record NewsQueryRequest(
    @NotBlank
    @Size(max = 500)
    String query,
    Location userLocation,
    @Positive
    @Max(100)
    Integer maxResults,
    @Positive
    @Max(500)
    Double radiusKm,
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    Double scoreThreshold
) {

    public int resolvedLimit() {
        return maxResults != null ? Math.min(maxResults, 50) : 10;
    }

    public double resolvedRadiusKm() {
        return radiusKm != null ? Math.min(radiusKm, 100.0) : 10.0;
    }

    public double resolvedScoreThreshold() {
        return scoreThreshold != null ? scoreThreshold : 0.0;
    }

    public record Location(
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        double latitude,
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        double longitude
    ) {
    }
}
