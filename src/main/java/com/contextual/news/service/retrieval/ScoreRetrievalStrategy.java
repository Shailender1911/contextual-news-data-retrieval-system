package com.contextual.news.service.retrieval;

import com.contextual.news.domain.model.NewsArticle;
import com.contextual.news.domain.repository.NewsArticleRepository;
import com.contextual.news.llm.model.ParsedQuery;
import com.contextual.news.llm.model.QueryIntent;
import com.contextual.news.service.model.RetrievedArticle;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class ScoreRetrievalStrategy implements ArticleRetrievalStrategy {

    private final NewsArticleRepository repository;

    public ScoreRetrievalStrategy(NewsArticleRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean supports(RetrievalContext context) {
        ParsedQuery parsed = context.parsedQuery();
        return parsed.hasIntent(QueryIntent.SCORE) && parsed.filters().scoreThreshold() != null;
    }

    @Override
    public List<RetrievedArticle> retrieve(RetrievalContext context, int limit) {
        ParsedQuery parsed = context.parsedQuery();
        Double threshold = parsed.filters().scoreThreshold();
        if (threshold == null) {
            return Collections.emptyList();
        }
        Specification<NewsArticle> spec = RetrievalSupport.baseSpecification(parsed);
        spec = RetrievalSupport.applyNearbyBoundingBox(context, spec);
        return repository.findAll(spec, RetrievalSupport.pageable(limit)).stream()
            .map(article -> new RetrievedArticle(article, strategyName(), article.getRelevanceScore() != null ? article.getRelevanceScore() : 0.0))
            .collect(Collectors.toList());
    }

    @Override
    public String strategyName() {
        return "score";
    }
}
