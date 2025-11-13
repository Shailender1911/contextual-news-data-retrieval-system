package com.contextual.news.service.model;

import com.contextual.news.domain.model.NewsArticle;
import java.util.Objects;

public record ArticleScore(
    NewsArticle article,
    double finalScore,
    Double distanceKm,
    String matchReason,
    double relevanceContribution,
    double recencyContribution,
    double semanticContribution,
    double proximityContribution
) {
    public ArticleScore {
        Objects.requireNonNull(article, "article must not be null");
    }
}
