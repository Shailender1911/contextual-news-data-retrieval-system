package com.contextual.news.llm.model;

public enum QueryIntent {
    CATEGORY,
    SCORE,
    SEARCH,
    SOURCE,
    NEARBY,
    UNKNOWN;

    public static QueryIntent fromString(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return switch (value.trim().toLowerCase()) {
            case "category" -> CATEGORY;
            case "score" -> SCORE;
            case "source" -> SOURCE;
            case "nearby" -> NEARBY;
            case "search" -> SEARCH;
            default -> UNKNOWN;
        };
    }
}
