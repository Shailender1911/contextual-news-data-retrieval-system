package com.contextual.news.service.model;

import com.contextual.news.domain.model.NewsArticle;
import java.util.Objects;

public record RetrievedArticle(
    NewsArticle article,
    String strategy,
    double primaryScore
) {
    public RetrievedArticle {
        Objects.requireNonNull(article, "article must not be null");
        strategy = strategy != null ? strategy : "unknown";
    }
}
