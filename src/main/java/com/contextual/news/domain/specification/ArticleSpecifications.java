package com.contextual.news.domain.specification;

import com.contextual.news.domain.model.NewsArticle;
import com.contextual.news.domain.model.NewsArticle;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.SetJoin;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

public final class ArticleSpecifications {

    private static final Set<String> STOP_WORDS = Set.of(
        "news", "latest", "today", "top", "breaking", "update", "updates", "near", "about", "around"
    );

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
            if (term == null || term.isBlank()) {
                return cb.conjunction();
            }
            String normalized = term.toLowerCase(Locale.ENGLISH).trim();
            Predicate titleLikePhrase = cb.like(cb.lower(root.get("title")), "%" + normalized + "%");
            Predicate descriptionLikePhrase = cb.like(cb.lower(root.get("description")), "%" + normalized + "%");

            List<Predicate> tokenPredicates = new ArrayList<>();
            for (String token : normalized.split("\\s+")) {
                String trimmed = token.trim();
                if (trimmed.length() < 3 || STOP_WORDS.contains(trimmed)) {
                    continue;
                }
                String like = "%" + trimmed + "%";
                tokenPredicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like)
                ));
            }

            Predicate phrasePredicate = cb.or(titleLikePhrase, descriptionLikePhrase);
            if (tokenPredicates.isEmpty()) {
                return phrasePredicate;
            }

            Predicate allTokens = cb.and(tokenPredicates.toArray(new Predicate[0]));
            return cb.or(phrasePredicate, allTokens);
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
