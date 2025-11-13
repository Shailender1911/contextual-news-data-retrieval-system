package com.contextual.news.service.dto;

import com.contextual.news.domain.model.NewsArticle;
import com.contextual.news.service.model.ArticleScore;

public record EnrichmentRequest(
    NewsArticle article,
    String userQuery,
    Double userLatitude,
    Double userLongitude,
    ArticleScore score
) {
}
