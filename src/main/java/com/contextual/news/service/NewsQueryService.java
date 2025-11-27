package com.contextual.news.service;

import com.contextual.news.api.dto.NewsQueryRequest;
import com.contextual.news.api.dto.NewsQueryResponse;
import com.contextual.news.api.dto.NewsQueryRequest;
import com.contextual.news.api.dto.NewsQueryResponse;
import com.contextual.news.llm.client.LLMClient;
import com.contextual.news.llm.model.ParsedQuery;
import com.contextual.news.service.dto.QueryUnderstandingContext;
import com.contextual.news.service.model.ArticleScore;
import com.contextual.news.service.model.RetrievedArticle;
import com.contextual.news.service.retrieval.ArticleRetrievalService;
import com.contextual.news.service.retrieval.RetrievalContext;
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
    private final ArticleResponseAssembler responseAssembler;

    public NewsQueryService(LLMClient llmClient,
                            ArticleRetrievalService retrievalService,
                            ArticleRankingService rankingService,
                            ArticleResponseAssembler responseAssembler) {
        this.llmClient = llmClient;
        this.retrievalService = retrievalService;
        this.rankingService = rankingService;
        this.responseAssembler = responseAssembler;
    }

    @Transactional(readOnly = true)
    public NewsQueryResponse query(NewsQueryRequest request) {
        ParsedQuery parsedQuery = llmClient.parseQuery(QueryUnderstandingContext.fromRequest(request));
        return executeQuery(request, parsedQuery);
    }

    @Transactional(readOnly = true)
    public NewsQueryResponse queryWithParsedQuery(NewsQueryRequest request, ParsedQuery parsedQuery) {
        return executeQuery(request, parsedQuery);
    }

    private NewsQueryResponse executeQuery(NewsQueryRequest request, ParsedQuery parsedQuery) {
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
        Map<com.contextual.news.domain.model.NewsArticle, com.contextual.news.llm.model.ArticleEnrichment> enrichmentMap =
            responseAssembler.enrichTopArticles(top, request.query(), adjusted.filters().latitude(), adjusted.filters().longitude());

        List<NewsQueryResponse.ArticleResult> articles = top.stream()
            .map(score -> responseAssembler.toArticleResult(score, enrichmentMap.get(score.article())))
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
