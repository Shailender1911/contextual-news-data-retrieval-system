package com.contextual.news.service;

import com.contextual.news.api.dto.NewsQueryResponse;
import com.contextual.news.config.AppProperties;
import com.contextual.news.domain.model.NewsArticle;
import com.contextual.news.llm.client.LLMClient;
import com.contextual.news.llm.model.ArticleEnrichment;
import com.contextual.news.service.dto.EnrichmentRequest;
import com.contextual.news.service.model.ArticleScore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ArticleResponseAssembler {

    private final LLMClient llmClient;
    private final AppProperties properties;

    public ArticleResponseAssembler(LLMClient llmClient, AppProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public Map<NewsArticle, ArticleEnrichment> enrichTopArticles(
        List<ArticleScore> scores,
        String query,
        Double latitude,
        Double longitude
    ) {
        int topN = Math.min(properties.enrichment().getTopN(), scores.size());
        Map<NewsArticle, ArticleEnrichment> enrichment = new HashMap<>();
        for (int i = 0; i < topN; i++) {
            ArticleScore score = scores.get(i);
            ArticleEnrichment articleEnrichment = llmClient.generateEnrichment(new EnrichmentRequest(
                score.article(),
                query,
                latitude,
                longitude,
                score
            ));
            enrichment.put(score.article(), articleEnrichment);
        }
        return enrichment;
    }

    public NewsQueryResponse.ArticleResult toArticleResult(ArticleScore score, ArticleEnrichment enrichment) {
        NewsArticle article = score.article();
        ArticleEnrichment safeEnrichment = enrichment != null ? enrichment : ArticleEnrichment.empty();
        return new NewsQueryResponse.ArticleResult(
            article.getId(),
            article.getTitle(),
            article.getDescription(),
            article.getUrl(),
            article.getPublicationDate(),
            article.getSourceName(),
            new ArrayList<>(article.getCategories()),
            article.getRelevanceScore(),
            score.finalScore(),
            score.distanceKm(),
            score.matchReason(),
            new NewsQueryResponse.Enrichment(
                safeEnrichment.summary(),
                safeEnrichment.keyEntities(),
                safeEnrichment.whyRelevant()
            )
        );
    }
}

