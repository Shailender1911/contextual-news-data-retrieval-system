package com.contextual.news.domain.specification;

import com.contextual.news.domain.model.NewsArticle;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.SetJoin;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.stream.Stream;

import org.springframework.data.jpa.domain.Specification;

public final class ArticleSpecifications {

    private ArticleSpecifications() {
    }

    public static Specification<NewsArticle> hasCategory(String category) {
        return (root, query, cb) -> {
            query.distinct(true);
            SetJoin<NewsArticle, String> categoriesJoin = root.joinSet("categories");
            return cb.equal(cb.lower(categoriesJoin), category.toLowerCase());
        };
    }

    public static Specification<NewsArticle> hasSource(String source) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("sourceName")), source.toLowerCase());
    }

    public static Specification<NewsArticle> hasMinimumScore(Double minimumScore) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("relevanceScore"), minimumScore);
    }

    public static Specification<NewsArticle> matchesSearchTerm(String term) {
        return (root, query, cb) -> {
            String normalized = term.toLowerCase();
            String[] tokens = normalized.split("\\s+");
            var titlePath = cb.lower(root.get("title"));
            var descriptionPath = cb.lower(root.get("description"));

            Predicate[] predicates = Arrays.stream(tokens)
                .filter(token -> token.length() > 2)
                .map(token -> "%" + token + "%")
                .flatMap(like -> Stream.of(
                    cb.like(titlePath, like),
                    cb.like(descriptionPath, like)
                ))
                .toArray(Predicate[]::new);

            return predicates.length == 0 ? cb.conjunction() : cb.or(predicates);
        };
    }

    public static Specification<NewsArticle> publishedAfter(OffsetDateTime dateTime) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("publicationDate"), dateTime);
    }

    public static Specification<NewsArticle> publishedBefore(OffsetDateTime dateTime) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("publicationDate"), dateTime);
    }

    public static Specification<NewsArticle> withinBoundingBox(double minLat, double maxLat, double minLon, double maxLon) {
        return (root, query, cb) -> cb.and(
            cb.between(root.get("latitude"), minLat, maxLat),
            cb.between(root.get("longitude"), minLon, maxLon)
        );
    }
}
