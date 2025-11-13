package com.contextual.news.service.retrieval;

import com.contextual.news.api.dto.NewsQueryRequest;
import com.contextual.news.domain.model.NewsArticle;
import com.contextual.news.domain.specification.ArticleSpecifications;
import com.contextual.news.llm.model.ParsedQuery;
import com.contextual.news.llm.model.QueryIntent;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public final class RetrievalSupport {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private RetrievalSupport() {
    }

    public static Specification<NewsArticle> baseSpecification(ParsedQuery parsedQuery) {
        Specification<NewsArticle> spec = Specification.where(null);
        var filters = parsedQuery.filters();

        if (filters.category() != null && !filters.category().isBlank()) {
            spec = spec.and(ArticleSpecifications.hasCategory(filters.category()));
        }
        if (filters.source() != null && !filters.source().isBlank()) {
            spec = spec.and(ArticleSpecifications.hasSource(filters.source()));
        }
        if (filters.scoreThreshold() != null) {
            spec = spec.and(ArticleSpecifications.hasMinimumScore(filters.scoreThreshold()));
        }
        if (parsedQuery.searchQuery() != null && !parsedQuery.searchQuery().isBlank()) {
            spec = spec.and(ArticleSpecifications.matchesSearchTerm(parsedQuery.searchQuery()));
        }
        if (filters.dateFrom() != null) {
            spec = spec.and(ArticleSpecifications.publishedAfter(filters.dateFrom()));
        }
        if (filters.dateTo() != null) {
            spec = spec.and(ArticleSpecifications.publishedBefore(filters.dateTo()));
        }
        return spec;
    }

    public static Pageable pageable(int limit) {
        Sort sort = Sort.by(Sort.Order.desc("relevanceScore"), Sort.Order.desc("publicationDate"));
        return PageRequest.of(0, limit, sort);
    }

    public static Specification<NewsArticle> applyNearbyBoundingBox(RetrievalContext context, Specification<NewsArticle> spec) {
        Double lat = resolveLatitude(context);
        Double lon = resolveLongitude(context);
        if (lat == null || lon == null) {
            return spec;
        }

        double radiusKm = resolveRadius(context);
        double latRadians = Math.toRadians(lat);
        double latDelta = Math.toDegrees(radiusKm / EARTH_RADIUS_KM);
        double lonDelta = Math.toDegrees(radiusKm / (EARTH_RADIUS_KM * Math.cos(latRadians)));

        double minLat = Math.max(-90.0, lat - latDelta);
        double maxLat = Math.min(90.0, lat + latDelta);
        double minLon = Math.max(-180.0, lon - lonDelta);
        double maxLon = Math.min(180.0, lon + lonDelta);

        return spec.and(ArticleSpecifications.withinBoundingBox(minLat, maxLat, minLon, maxLon));
    }

    public static boolean requiresNearbyIntent(ParsedQuery parsedQuery) {
        return parsedQuery.hasIntent(QueryIntent.NEARBY);
    }

    public static Double resolveLatitude(RetrievalContext context) {
        ParsedQuery parsedQuery = context.parsedQuery();
        NewsQueryRequest request = context.request();
        return Optional.ofNullable(parsedQuery.filters().latitude())
            .orElseGet(() -> Optional.ofNullable(request.userLocation()).map(NewsQueryRequest.Location::latitude).orElse(null));
    }

    public static Double resolveLongitude(RetrievalContext context) {
        ParsedQuery parsedQuery = context.parsedQuery();
        NewsQueryRequest request = context.request();
        return Optional.ofNullable(parsedQuery.filters().longitude())
            .orElseGet(() -> Optional.ofNullable(request.userLocation()).map(NewsQueryRequest.Location::longitude).orElse(null));
    }

    public static double resolveRadius(RetrievalContext context) {
        ParsedQuery parsedQuery = context.parsedQuery();
        NewsQueryRequest request = context.request();
        return Optional.ofNullable(parsedQuery.filters().radiusKm()).orElseGet(request::resolvedRadiusKm);
    }
}
