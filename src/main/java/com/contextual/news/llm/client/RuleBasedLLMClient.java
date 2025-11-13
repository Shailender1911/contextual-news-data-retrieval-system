package com.contextual.news.llm.client;

import com.contextual.news.domain.model.NewsArticle;
import com.contextual.news.llm.model.ArticleEnrichment;
import com.contextual.news.llm.model.ParsedQuery;
import com.contextual.news.llm.model.QueryIntent;
import com.contextual.news.service.dto.EnrichmentRequest;
import com.contextual.news.service.dto.QueryUnderstandingContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedLLMClient implements LLMClient {

    private static final Pattern ENTITY_PATTERN = Pattern.compile("\\b([A-Z][a-zA-Z]+(?:\\s+[A-Z][a-zA-Z]+)*)\\b");
    private static final Set<String> KNOWN_CATEGORIES = Set.of(
        "general", "technology", "business", "sports", "entertainment", "health", "science", "politics", "world"
    );

    @Override
    public ParsedQuery parseQuery(QueryUnderstandingContext context) {
        if (context == null || context.query() == null) {
            return ParsedQuery.fallback("");
        }
        String query = context.query();
        String normalized = query.toLowerCase(Locale.ENGLISH);
        EnumSet<QueryIntent> intents = EnumSet.noneOf(QueryIntent.class);
        ParsedQuery.Filters filters = new ParsedQuery.Filters(null, null, context.scoreThreshold(), context.radiusKm(), context.latitude(), context.longitude(), null, null);

        String detectedCategory = detectCategory(normalized);
        if (detectedCategory != null) {
            intents.add(QueryIntent.CATEGORY);
            filters = new ParsedQuery.Filters(detectedCategory, filters.source(), filters.scoreThreshold(), filters.radiusKm(), filters.latitude(), filters.longitude(), null, null);
        }

        String detectedSource = detectSource(query);
        if (detectedSource != null) {
            intents.add(QueryIntent.SOURCE);
            filters = new ParsedQuery.Filters(filters.category(), detectedSource, filters.scoreThreshold(), filters.radiusKm(), filters.latitude(), filters.longitude(), null, null);
        }

        if (normalized.contains("near") || normalized.contains("around") || normalized.contains("close to")) {
            intents.add(QueryIntent.NEARBY);
        }

        if (normalized.contains("score") || normalized.contains("rank") || context.scoreThreshold() != null) {
            intents.add(QueryIntent.SCORE);
        }

        if (intents.isEmpty()) {
            intents.add(QueryIntent.SEARCH);
        } else if (!intents.contains(QueryIntent.SEARCH)) {
            intents.add(QueryIntent.SEARCH);
        }

        List<String> entities = extractEntities(query);
        List<String> concepts = extractKeywords(normalized);

        String search = buildSearchQuery(query, detectedCategory, detectedSource);

        return ParsedQuery.create(entities, concepts, intents, filters, search, false);
    }

    @Override
    public ArticleEnrichment generateEnrichment(EnrichmentRequest request) {
        NewsArticle article = request.article();
        String summary = buildSummary(article);
        List<String> keyEntities = extractEntities(article.getTitle() + " " + article.getDescription());
        String whyRelevant = buildWhyRelevant(request, keyEntities);
        return new ArticleEnrichment(summary, keyEntities, whyRelevant);
    }

    private String buildSummary(NewsArticle article) {
        String source = article.getSourceName() != null ? " from " + article.getSourceName() : "";
        String base = article.getDescription() != null && !article.getDescription().isBlank()
            ? article.getDescription()
            : article.getTitle();
        if (base == null) {
            return null;
        }
        String summary = base.length() > 220 ? base.substring(0, 217) + "..." : base;
        return summary + source;
    }

    private String buildWhyRelevant(EnrichmentRequest request, List<String> keyEntities) {
        List<String> reasons = new ArrayList<>();
        if (request.score().matchReason() != null) {
            reasons.add("Matched by " + request.score().matchReason());
        }
        if (request.userLatitude() != null && request.userLongitude() != null && request.article().getLatitude() != null) {
            reasons.add("Geographically relevant to your location");
        }
        if (!keyEntities.isEmpty()) {
            reasons.add("Highlights: " + String.join(", ", keyEntities.stream().limit(3).toList()));
        }
        return reasons.isEmpty() ? null : String.join(". ", reasons);
    }

    private String detectCategory(String normalizedQuery) {
        return KNOWN_CATEGORIES.stream()
            .filter(normalizedQuery::contains)
            .findFirst()
            .orElse(null);
    }

    private String detectSource(String query) {
        if (query == null) {
            return null;
        }
        if (query.toLowerCase(Locale.ENGLISH).contains("new york times")) {
            return "New York Times";
        }
        if (query.toLowerCase(Locale.ENGLISH).contains("reuters")) {
            return "Reuters";
        }
        if (query.toLowerCase(Locale.ENGLISH).contains("bbc")) {
            return "BBC";
        }
        return null;
    }

    private List<String> extractEntities(String text) {
        if (text == null) {
            return List.of();
        }
        Matcher matcher = ENTITY_PATTERN.matcher(text);
        List<String> entities = new ArrayList<>();
        while (matcher.find()) {
            String candidate = matcher.group(1).trim();
            if (candidate.length() > 2 && Character.isUpperCase(candidate.charAt(0))) {
                entities.add(candidate);
            }
        }
        return entities.stream().distinct().collect(Collectors.toList());
    }

    private List<String> extractKeywords(String normalizedQuery) {
        if (normalizedQuery == null) {
            return List.of();
        }
        String[] tokens = normalizedQuery.split("\\s+");
        return Arrays.stream(tokens)
            .filter(token -> token.length() > 3)
            .distinct()
            .limit(10)
            .collect(Collectors.toList());
    }

    private String buildSearchQuery(String query, String category, String source) {
        StringBuilder sb = new StringBuilder(query);
        if (category != null) {
            sb.append(' ').append(category);
        }
        if (source != null) {
            sb.append(' ').append(source);
        }
        return sb.toString();
    }
}
