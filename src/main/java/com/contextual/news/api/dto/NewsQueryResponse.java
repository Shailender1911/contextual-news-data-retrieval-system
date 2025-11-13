package com.contextual.news.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NewsQueryResponse(
    QueryMetadata metadata,
    List<ArticleResult> articles
) {

    public record QueryMetadata(
        List<String> intents,
        List<String> entities,
        List<String> concepts,
        QueryFilters filters,
        boolean llmFallbackUsed
    ) {
    }

    public record QueryFilters(
        String category,
        String source,
        Double scoreThreshold,
        Double radiusKm,
        Double latitude,
        Double longitude,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo
    ) {
    }

    public record ArticleResult(
        UUID id,
        String title,
        String description,
        String url,
        OffsetDateTime publicationDate,
        String sourceName,
        List<String> categories,
        Double relevanceScore,
        double finalScore,
        Double distanceKm,
        String matchReason,
        Enrichment enrichment
    ) {
    }

    public record Enrichment(
        String summary,
        List<String> keyEntities,
        String whyRelevant
    ) {
    }
}
