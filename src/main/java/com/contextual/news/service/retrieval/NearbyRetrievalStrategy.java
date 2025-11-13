package com.contextual.news.service.retrieval;

import com.contextual.news.domain.model.NewsArticle;
import com.contextual.news.domain.repository.NewsArticleRepository;
import com.contextual.news.llm.model.QueryIntent;
import com.contextual.news.service.model.RetrievedArticle;
import com.contextual.news.service.util.GeoUtils;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class NearbyRetrievalStrategy implements ArticleRetrievalStrategy {

    private final NewsArticleRepository repository;

    public NearbyRetrievalStrategy(NewsArticleRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean supports(RetrievalContext context) {
        return context.parsedQuery().hasIntent(QueryIntent.NEARBY)
            && RetrievalSupport.resolveLatitude(context) != null
            && RetrievalSupport.resolveLongitude(context) != null;
    }

    @Override
    public List<RetrievedArticle> retrieve(RetrievalContext context, int limit) {
        Double userLat = RetrievalSupport.resolveLatitude(context);
        Double userLon = RetrievalSupport.resolveLongitude(context);
        if (userLat == null || userLon == null) {
            return Collections.emptyList();
        }
        double radiusKm = RetrievalSupport.resolveRadius(context);
        Specification<NewsArticle> spec = RetrievalSupport.applyNearbyBoundingBox(context,
            RetrievalSupport.baseSpecification(context.parsedQuery()));
        return repository.findAll(spec, RetrievalSupport.pageable(limit)).stream()
            .map(article -> {
                double distance = GeoUtils.distanceKm(userLat, userLon, article.getLatitude(), article.getLongitude());
                double proximityScore = Math.max(0.0, 1.0 - Math.min(distance / radiusKm, 1.0));
                return new RetrievedArticle(article, strategyName(), proximityScore);
            })
            .collect(Collectors.toList());
    }

    @Override
    public String strategyName() {
        return "nearby";
    }
}
