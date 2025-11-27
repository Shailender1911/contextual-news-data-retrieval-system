package com.contextual.news.service.trending;

import com.contextual.news.api.dto.NewsQueryResponse;
import com.contextual.news.api.dto.TrendingEventRequest;
import com.contextual.news.api.dto.TrendingResponse;
import com.contextual.news.domain.model.ArticleTrendAggregate;
import com.contextual.news.domain.model.ArticleTrendAggregateId;
import com.contextual.news.domain.model.NewsArticle;
import com.contextual.news.domain.repository.ArticleTrendAggregateRepository;
import com.contextual.news.domain.repository.NewsArticleRepository;
import com.contextual.news.llm.model.ArticleEnrichment;
import com.contextual.news.service.ArticleResponseAssembler;
import com.contextual.news.service.model.ArticleScore;
import com.contextual.news.service.util.GeoBucketer;
import com.contextual.news.service.util.GeoUtils;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrendingService {

    private static final double DEFAULT_RADIUS_KM = 25.0;
    private static final int DEFAULT_LIMIT = 5;
    private static final double BUCKET_SIZE_DEGREES = 0.5; // ~55km
    private static final double HALF_LIFE_MINUTES = 360.0; // 6 hours
    private static final double LAMBDA = Math.log(2) / HALF_LIFE_MINUTES;

    private final ArticleTrendAggregateRepository trendRepository;
    private final NewsArticleRepository articleRepository;
    private final ArticleResponseAssembler responseAssembler;
    private final Cache trendingCache;
    private final GeoBucketer geoBucketer;
    private final Clock clock;

    public TrendingService(ArticleTrendAggregateRepository trendRepository,
                           NewsArticleRepository articleRepository,
                           ArticleResponseAssembler responseAssembler,
                           CacheManager cacheManager,
                           Clock clock) {
        this.trendRepository = trendRepository;
        this.articleRepository = articleRepository;
        this.responseAssembler = responseAssembler;
        this.trendingCache = cacheManager.getCache("trending-feed");
        this.geoBucketer = new GeoBucketer(BUCKET_SIZE_DEGREES);
        this.clock = clock;
    }

    @Transactional
    public void recordEvent(TrendingEventRequest request) {
        OffsetDateTime occurredAt = request.occurredAt() != null ? request.occurredAt() : OffsetDateTime.now(clock);
        TrendingEventRequest.Location location = request.userLocation();
        NewsArticle article = articleRepository.findById(request.articleId())
            .orElseThrow(() -> new IllegalArgumentException("Article not found: " + request.articleId()));

        double latitude = location != null ? location.latitude() : article.getLatitude();
        double longitude = location != null ? location.longitude() : article.getLongitude();

        String bucketId = geoBucketer.bucketId(latitude, longitude);
        ArticleTrendAggregate aggregate = trendRepository.findById(new ArticleTrendAggregateId(bucketId, article.getId()))
            .orElseGet(() -> new ArticleTrendAggregate(bucketId, article.getId(), occurredAt));

        aggregate.registerEvent(request.eventType().weight(), occurredAt, LAMBDA);
        trendRepository.save(aggregate);
        evictBucket(bucketId);
    }

    @Transactional(readOnly = true)
    public TrendingResponse getTrendingFeed(double latitude, double longitude, Double radiusKm, Integer limitOverride) {
        double radius = radiusKm != null ? Math.max(1.0, Math.min(radiusKm, 200.0)) : DEFAULT_RADIUS_KM;
        int limit = limitOverride != null ? Math.max(1, Math.min(limitOverride, 20)) : DEFAULT_LIMIT;
        String primaryBucket = geoBucketer.bucketId(latitude, longitude);
        String cacheKey = cacheKey(primaryBucket, radius, limit);

        if (trendingCache != null) {
            TrendingResponse cached = trendingCache.get(cacheKey, TrendingResponse.class);
            if (cached != null) {
                TrendingResponse.TrendingMetadata metadata = new TrendingResponse.TrendingMetadata(
                    latitude,
                    longitude,
                    radius,
                    limit,
                    true,
                    cached.metadata().bucketId()
                );
                return new TrendingResponse(metadata, cached.articles());
            }
        }

        List<String> bucketIds = geoBucketer.nearbyBuckets(latitude, longitude, radius);
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<ArticleTrendAggregate> aggregates = trendRepository.findByIdBucketIdIn(bucketIds);
        if (aggregates.isEmpty()) {
            TrendingResponse response = new TrendingResponse(
                new TrendingResponse.TrendingMetadata(latitude, longitude, radius, limit, false, primaryBucket),
                List.of()
            );
            cacheResponse(cacheKey, response);
            return response;
        }

        Map<UUID, List<ArticleTrendAggregate>> aggregatesByArticle = aggregates.stream()
            .collect(Collectors.groupingBy(aggregate -> aggregate.getId().getArticleId()));

        List<NewsArticle> articles = articleRepository.findAllById(aggregatesByArticle.keySet());
        Map<UUID, NewsArticle> articleMap = articles.stream()
            .collect(Collectors.toMap(NewsArticle::getId, a -> a));

        List<ArticleScore> scores = new ArrayList<>();
        for (Map.Entry<UUID, List<ArticleTrendAggregate>> entry : aggregatesByArticle.entrySet()) {
            NewsArticle article = articleMap.get(entry.getKey());
            if (article == null) {
                continue;
            }
            double bestScore = 0.0;
            double distanceKm = GeoUtils.distanceKm(latitude, longitude, article.getLatitude(), article.getLongitude());
            for (ArticleTrendAggregate aggregate : entry.getValue()) {
                bestScore = Math.max(bestScore, aggregate.decayedScore(now, LAMBDA));
            }
            if (bestScore <= 0.0) {
                continue;
            }
            scores.add(new ArticleScore(
                article,
                bestScore,
                distanceKm,
                "trending",
                bestScore,
                0.0,
                0.0,
                0.0
            ));
        }

        if (scores.isEmpty()) {
            TrendingResponse response = new TrendingResponse(
                new TrendingResponse.TrendingMetadata(latitude, longitude, radius, limit, false, primaryBucket),
                List.of()
            );
            cacheResponse(cacheKey, response);
            return response;
        }

        scores.sort(Comparator.comparingDouble(ArticleScore::finalScore).reversed());
        List<ArticleScore> topScores = scores.stream().limit(limit).collect(Collectors.toList());

        Map<NewsArticle, ArticleEnrichment> enrichment =
            responseAssembler.enrichTopArticles(topScores, null, latitude, longitude);
        List<NewsQueryResponse.ArticleResult> results = topScores.stream()
            .map(score -> responseAssembler.toArticleResult(score, enrichment.get(score.article())))
            .toList();

        TrendingResponse response = new TrendingResponse(
            new TrendingResponse.TrendingMetadata(latitude, longitude, radius, limit, false, primaryBucket),
            results
        );
        cacheResponse(cacheKey, response);
        return response;
    }

    private void cacheResponse(String cacheKey, TrendingResponse response) {
        if (trendingCache != null) {
            trendingCache.put(cacheKey, response);
        }
    }

    private void evictBucket(String bucketId) {
        if (trendingCache != null) {
            trendingCache.clear(); // conservative eviction
        }
    }

    private String cacheKey(String bucketId, double radius, int limit) {
        return bucketId + "|" + Math.round(radius * 10.0) / 10.0 + "|" + limit;
    }

    public TrendingEventRequest randomEvent() {
        long totalArticles = articleRepository.count();
        if (totalArticles == 0) {
            throw new IllegalStateException("No articles available for simulation");
        }
        int index = ThreadLocalRandom.current().nextInt((int) totalArticles);
        NewsArticle article = articleRepository.findAll(org.springframework.data.domain.PageRequest.of(index, 1))
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to fetch random article"));
        TrendingEventRequest.EventType[] types = TrendingEventRequest.EventType.values();
        TrendingEventRequest.EventType eventType = types[ThreadLocalRandom.current().nextInt(types.length)];
        return new TrendingEventRequest(
            eventType,
            article.getId(),
            new TrendingEventRequest.Location(article.getLatitude(), article.getLongitude()),
            OffsetDateTime.now(clock)
        );
    }
}

