package com.contextual.news.service.retrieval;

import com.contextual.news.service.model.RetrievedArticle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ArticleRetrievalService {

    private final List<ArticleRetrievalStrategy> strategies;

    public ArticleRetrievalService(List<ArticleRetrievalStrategy> strategies) {
        this.strategies = strategies;
    }

    public List<RetrievedArticle> retrieveArticles(RetrievalContext context, int limit) {
        Map<UUID, RetrievedArticle> aggregated = new LinkedHashMap<>();
        int fetchMultiplier = 3;
        for (ArticleRetrievalStrategy strategy : strategies) {
            if (!strategy.supports(context)) {
                continue;
            }
            List<RetrievedArticle> retrieved = strategy.retrieve(context, limit * fetchMultiplier);
            for (RetrievedArticle candidate : retrieved) {
                aggregated.putIfAbsent(candidate.article().getId(), candidate);
            }
        }
        if (aggregated.isEmpty()) {
            strategies.stream()
                .filter(strategy -> "search".equals(strategy.strategyName()))
                .findFirst()
                .ifPresent(strategy -> strategy.retrieve(context, limit * fetchMultiplier).forEach(candidate ->
                    aggregated.putIfAbsent(candidate.article().getId(), candidate)));
        }
        return new ArrayList<>(aggregated.values()).stream()
            .limit(limit * fetchMultiplier)
            .collect(Collectors.toList());
    }
}
