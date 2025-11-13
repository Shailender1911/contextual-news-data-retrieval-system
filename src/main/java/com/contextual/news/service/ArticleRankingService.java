package com.contextual.news.service;

import com.contextual.news.api.dto.NewsQueryRequest;
import com.contextual.news.config.AppProperties;
import com.contextual.news.domain.model.NewsArticle;
import com.contextual.news.llm.model.ParsedQuery;
import com.contextual.news.llm.model.QueryIntent;
import com.contextual.news.service.model.ArticleScore;
import com.contextual.news.service.model.RetrievedArticle;
import com.contextual.news.service.retrieval.RetrievalContext;
import com.contextual.news.service.retrieval.RetrievalSupport;
import com.contextual.news.service.util.GeoUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ArticleRankingService {

    private final AppProperties properties;
    private final Clock clock;

    public ArticleRankingService(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public List<ArticleScore> scoreArticles(List<RetrievedArticle> candidates, RetrievalContext context) {
        ParsedQuery parsedQuery = context.parsedQuery();
        NewsQueryRequest request = context.request();
        List<ArticleScore> scores = new ArrayList<>(candidates.size());
        for (RetrievedArticle candidate : candidates) {
            ArticleScore score = scoreCandidate(candidate, parsedQuery, request);
            scores.add(score);
        }
        return scores.stream()
            .sorted(Comparator.comparingDouble(ArticleScore::finalScore).reversed())
            .collect(Collectors.toList());
    }

    private ArticleScore scoreCandidate(RetrievedArticle candidate, ParsedQuery parsedQuery, NewsQueryRequest request) {
        NewsArticle article = candidate.article();
        AppProperties.RankingProperties ranking = properties.ranking();

        double relevanceContribution = normalize(article.getRelevanceScore());
        double recencyContribution = calculateRecencyContribution(article.getPublicationDate(), ranking.getRecencyHalfLifeDays());
        double semanticContribution = calculateSemanticContribution(parsedQuery, request.query(), article);
        double proximityContribution = calculateProximityContribution(candidate, request);

        double finalScore = ranking.getRelevanceWeight() * relevanceContribution
            + ranking.getRecencyWeight() * recencyContribution
            + ranking.getSemanticWeight() * semanticContribution
            + ranking.getProximityWeight() * proximityContribution;

        Double distance = null;
        Double userLat = RetrievalSupport.resolveLatitude(new RetrievalContext(request, parsedQuery));
        Double userLon = RetrievalSupport.resolveLongitude(new RetrievalContext(request, parsedQuery));
        if (userLat != null && userLon != null && article.getLatitude() != null && article.getLongitude() != null) {
            distance = GeoUtils.distanceKm(userLat, userLon, article.getLatitude(), article.getLongitude());
        }

        String matchReason = determineMatchReason(parsedQuery, candidate.strategy());

        return new ArticleScore(article, finalScore, distance, matchReason,
            relevanceContribution, recencyContribution, semanticContribution, proximityContribution);
    }

    private String determineMatchReason(ParsedQuery parsedQuery, String strategy) {
        if (parsedQuery.hasIntent(QueryIntent.NEARBY) && "nearby".equals(strategy)) {
            return "nearby";
        }
        if (parsedQuery.hasIntent(QueryIntent.CATEGORY) && "category".equals(strategy)) {
            return "category";
        }
        if (parsedQuery.hasIntent(QueryIntent.SOURCE) && "source".equals(strategy)) {
            return "source";
        }
        if (parsedQuery.hasIntent(QueryIntent.SCORE) && "score".equals(strategy)) {
            return "score";
        }
        return strategy;
    }

    private double calculateProximityContribution(RetrievedArticle candidate, NewsQueryRequest request) {
        if (request.userLocation() == null) {
            return 0.0;
        }
        if (!"nearby".equals(candidate.strategy())) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, candidate.primaryScore()));
    }

    private double calculateSemanticContribution(ParsedQuery parsedQuery, String query, NewsArticle article) {
        Set<String> queryTokens = tokenize(query);
        if (parsedQuery.searchQuery() != null) {
            queryTokens.addAll(tokenize(parsedQuery.searchQuery()));
        }
        if (queryTokens.isEmpty()) {
            return 0.0;
        }
        Set<String> articleTokens = new HashSet<>();
        articleTokens.addAll(tokenize(article.getTitle()));
        articleTokens.addAll(tokenize(article.getDescription()));
        if (articleTokens.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(articleTokens);
        intersection.retainAll(queryTokens);
        Set<String> union = new HashSet<>(articleTokens);
        union.addAll(queryTokens);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return new HashSet<>();
        }
        String normalized = text.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9 ]", " ");
        String[] tokens = normalized.split("\\s+");
        Set<String> result = new HashSet<>();
        for (String token : tokens) {
            if (token.length() > 2) {
                result.add(token);
            }
        }
        return result;
    }

    private double calculateRecencyContribution(OffsetDateTime publicationDate, double halfLifeDays) {
        if (publicationDate == null) {
            return 0.0;
        }
        Duration age = Duration.between(publicationDate, OffsetDateTime.now(clock));
        double days = age.toHours() / 24.0;
        if (days <= 0) {
            return 1.0;
        }
        double lambda = Math.log(2) / halfLifeDays;
        return Math.exp(-lambda * days);
    }

    private double normalize(Double value) {
        if (value == null) {
            return 0.0;
        }
        if (value.isNaN()) {
            return 0.0;
        }
        if (value < 0) {
            return 0.0;
        }
        if (value > 1) {
            return 1.0;
        }
        return value;
    }
}
