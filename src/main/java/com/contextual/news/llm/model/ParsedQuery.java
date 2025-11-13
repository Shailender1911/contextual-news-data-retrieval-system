package com.contextual.news.llm.model;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ParsedQuery {

    private final List<String> entities;
    private final List<String> concepts;
    private final Set<QueryIntent> intents;
    private final Filters filters;
    private final String searchQuery;
    private final boolean fallbackUsed;

    private ParsedQuery(List<String> entities,
    List<String> concepts,
    Set<QueryIntent> intents,
    Filters filters,
    String searchQuery,
                        boolean fallbackUsed) {
        this.entities = entities;
        this.concepts = concepts;
        this.intents = intents;
        this.filters = filters;
        this.searchQuery = searchQuery;
        this.fallbackUsed = fallbackUsed;
    }

    public static ParsedQuery create(List<String> entities,
                                     List<String> concepts,
                                     Set<QueryIntent> intents,
                                     Filters filters,
                                     String searchQuery,
                                     boolean fallbackUsed) {
        List<String> safeEntities = entities != null ? List.copyOf(entities) : List.of();
        List<String> safeConcepts = concepts != null ? List.copyOf(concepts) : List.of();
        EnumSet<QueryIntent> safeIntents = intents != null && !intents.isEmpty() ? EnumSet.copyOf(intents) : EnumSet.of(QueryIntent.SEARCH);
        safeIntents.add(QueryIntent.SEARCH);
        Filters safeFilters = filters != null ? filters : Filters.empty();
        String safeSearchQuery = Optional.ofNullable(searchQuery).filter(s -> !s.isBlank()).orElse(null);
        return new ParsedQuery(safeEntities, safeConcepts, Collections.unmodifiableSet(safeIntents), safeFilters, safeSearchQuery, fallbackUsed);
    }

    public boolean hasIntent(QueryIntent intent) {
        return intents.contains(intent);
    }

    public ParsedQuery withFallback() {
        if (fallbackUsed) {
            return this;
        }
        return new ParsedQuery(entities, concepts, intents, filters, searchQuery, true);
    }

    public static ParsedQuery fallback(String query) {
        return create(List.of(), List.of(), EnumSet.of(QueryIntent.SEARCH), Filters.empty(), query, true);
    }

    public List<String> entities() {
        return entities;
    }

    public List<String> concepts() {
        return concepts;
    }

    public Set<QueryIntent> intents() {
        return intents;
    }

    public Filters filters() {
        return filters;
    }

    public String searchQuery() {
        return searchQuery;
    }

    public boolean fallbackUsed() {
        return fallbackUsed;
    }

    public record Filters(
        String category,
        String source,
        Double scoreThreshold,
        Double radiusKm,
        Double latitude,
        Double longitude,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo
    ) {
        public static Filters empty() {
            return new Filters(null, null, null, null, null, null, null, null);
        }
    }
}
