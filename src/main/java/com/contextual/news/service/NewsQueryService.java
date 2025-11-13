package com.contextual.news.service;

import com.contextual.news.api.dto.NewsQueryRequest;
import com.contextual.news.api.dto.NewsQueryResponse;
import com.contextual.news.config.AppProperties;
import com.contextual.news.domain.model.NewsArticle;
import com.contextual.news.llm.client.LLMClient;
import com.contextual.news.llm.model.ArticleEnrichment;
import com.contextual.news.llm.model.ParsedQuery;
import com.contextual.news.service.dto.EnrichmentRequest;
import com.contextual.news.service.dto.QueryUnderstandingContext;
import com.contextual.news.service.model.ArticleScore;
import com.contextual.news.service.model.RetrievedArticle;
import com.contextual.news.service.retrieval.ArticleRetrievalService;
import com.contextual.news.service.retrieval.RetrievalContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NewsQueryService {

    private final LLMClient llmClient;
    private final ArticleRetrievalService retrievalService;
    private final ArticleRankingService rankingService;
    private final AppProperties properties;

    public NewsQueryService(LLMClient llmClient,
                            ArticleRetrievalService retrievalService,
                            ArticleRankingService rankingService,
                            AppProperties properties) {
        this.llmClient = llmClient;
        this.retrievalService = retrievalService;
        this.rankingService = rankingService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public NewsQueryResponse query(NewsQueryRequest request) {
        ParsedQuery parsedQuery = llmClient.parseQuery(QueryUnderstandingContext.fromRequest(request));
        ParsedQuery adjusted = enrichFiltersWithRequest(parsedQuery, request);

        RetrievalContext retrievalContext = new RetrievalContext(request, adjusted);
        int limit = request.resolvedLimit();
        List<RetrievedArticle> retrieved = retrievalService.retrieveArticles(retrievalContext, limit);
        if (retrieved.isEmpty()) {
            return new NewsQueryResponse(
                new NewsQueryResponse.QueryMetadata(
                    mapIntents(adjusted),
                    adjusted.entities(),
                    adjusted.concepts(),
                    mapFilters(adjusted.filters()),
                    adjusted.fallbackUsed()
                ),
                List.of()
            );
        }

        List<ArticleScore> scored = rankingService.scoreArticles(retrieved, retrievalContext);
        List<ArticleScore> top = scored.stream().limit(limit).collect(Collectors.toList());
        Map<NewsArticle, ArticleEnrichment> enrichmentMap = enrichArticles(top, request, adjusted);

        List<NewsQueryResponse.ArticleResult> articles = top.stream()
            .map(score -> mapArticle(score, enrichmentMap.getOrDefault(score.article(), ArticleEnrichment.empty())))
            .collect(Collectors.toList());

        return new NewsQueryResponse(
            new NewsQueryResponse.QueryMetadata(
                mapIntents(adjusted),
                adjusted.entities(),
                adjusted.concepts(),
                mapFilters(adjusted.filters()),
                adjusted.fallbackUsed()
            ),
            articles
        );
    }

    private ParsedQuery enrichFiltersWithRequest(ParsedQuery parsedQuery, NewsQueryRequest request) {
        ParsedQuery.Filters filters = parsedQuery.filters();
        Double scoreThreshold = filters.scoreThreshold() != null ? filters.scoreThreshold() : request.scoreThreshold();
        Double radiusKm = filters.radiusKm();
        if (radiusKm == null) {
            if (request.radiusKm() != null) {
                radiusKm = request.radiusKm();
            } else if (parsedQuery.hasIntent(com.contextual.news.llm.model.QueryIntent.NEARBY)) {
                radiusKm = request.resolvedRadiusKm();
            }
        }
        Double latitude = filters.latitude() != null ? filters.latitude() : (request.userLocation() != null ? request.userLocation().latitude() : null);
        Double longitude = filters.longitude() != null ? filters.longitude() : (request.userLocation() != null ? request.userLocation().longitude() : null);

        ParsedQuery.Filters merged = new ParsedQuery.Filters(
            filters.category(),
            filters.source(),
            scoreThreshold,
            radiusKm,
            latitude,
            longitude,
            filters.dateFrom(),
            filters.dateTo()
        );
        return ParsedQuery.create(parsedQuery.entities(), parsedQuery.concepts(), parsedQuery.intents(), merged, parsedQuery.searchQuery(), parsedQuery.fallbackUsed());
    }

    private Map<NewsArticle, ArticleEnrichment> enrichArticles(List<ArticleScore> scores, NewsQueryRequest request, ParsedQuery parsedQuery) {
        int topN = Math.min(properties.enrichment().getTopN(), scores.size());
        Map<NewsArticle, ArticleEnrichment> enrichment = new java.util.HashMap<>();
        for (int i = 0; i < topN; i++) {
            ArticleScore score = scores.get(i);
            ArticleEnrichment articleEnrichment = llmClient.generateEnrichment(new EnrichmentRequest(
                score.article(),
                request.query(),
                parsedQuery.filters().latitude(),
                parsedQuery.filters().longitude(),
                score
            ));
            enrichment.put(score.article(), articleEnrichment);
        }
        return enrichment;
    }

    private NewsQueryResponse.ArticleResult mapArticle(ArticleScore score, ArticleEnrichment enrichment) {
        NewsArticle article = score.article();
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
                enrichment.summary(),
                enrichment.keyEntities(),
                enrichment.whyRelevant()
            )
        );
    }

    private NewsQueryResponse.QueryFilters mapFilters(ParsedQuery.Filters filters) {
        return new NewsQueryResponse.QueryFilters(
            filters.category(),
            filters.source(),
            filters.scoreThreshold(),
            filters.radiusKm(),
            filters.latitude(),
            filters.longitude(),
            filters.dateFrom(),
            filters.dateTo()
        );
    }

    private List<String> mapIntents(ParsedQuery parsedQuery) {
        return parsedQuery.intents().stream()
            .filter(intent -> intent != null)
            .map(intent -> intent.name().toLowerCase())
            .toList();
    }
}
