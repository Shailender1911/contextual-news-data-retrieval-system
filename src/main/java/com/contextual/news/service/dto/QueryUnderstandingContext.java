package com.contextual.news.service.dto;

import com.contextual.news.api.dto.NewsQueryRequest;
import java.util.Optional;

public record QueryUnderstandingContext(
    String query,
    Double latitude,
    Double longitude,
    Double radiusKm,
    Double scoreThreshold
) {
    public static QueryUnderstandingContext fromRequest(NewsQueryRequest request) {
        Double lat = Optional.ofNullable(request.userLocation()).map(NewsQueryRequest.Location::latitude).orElse(null);
        Double lon = Optional.ofNullable(request.userLocation()).map(NewsQueryRequest.Location::longitude).orElse(null);
        return new QueryUnderstandingContext(
            request.query(),
            lat,
            lon,
            request.radiusKm(),
            request.scoreThreshold()
        );
    }
}
