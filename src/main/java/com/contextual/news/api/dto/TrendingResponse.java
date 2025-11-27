package com.contextual.news.api.dto;

import java.util.List;

public record TrendingResponse(
    TrendingMetadata metadata,
    List<NewsQueryResponse.ArticleResult> articles
) {

    public record TrendingMetadata(
        double latitude,
        double longitude,
        double radiusKm,
        int limit,
        boolean cacheHit,
        String bucketId
    ) {
    }
}

