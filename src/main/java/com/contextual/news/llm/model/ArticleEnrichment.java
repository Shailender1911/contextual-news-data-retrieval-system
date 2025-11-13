package com.contextual.news.llm.model;

import java.util.List;
import java.util.Objects;

public record ArticleEnrichment(
    String summary,
    List<String> keyEntities,
    String whyRelevant
) {
    public ArticleEnrichment {
        keyEntities = keyEntities != null ? List.copyOf(keyEntities) : List.of();
    }

    public static ArticleEnrichment empty() {
        return new ArticleEnrichment(null, List.of(), null);
    }

    public boolean isEmpty() {
        return (summary == null || summary.isBlank())
            && keyEntities.isEmpty()
            && (whyRelevant == null || whyRelevant.isBlank());
    }

    public ArticleEnrichment mergeMissing(ArticleEnrichment fallback) {
        if (fallback == null || fallback.isEmpty()) {
            return this;
        }
        return new ArticleEnrichment(
            summary != null ? summary : fallback.summary,
            keyEntities.isEmpty() ? fallback.keyEntities : keyEntities,
            whyRelevant != null ? whyRelevant : fallback.whyRelevant
        );
    }
}
