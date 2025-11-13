package com.contextual.news.service.retrieval;

import com.contextual.news.service.model.RetrievedArticle;
import java.util.List;

public interface ArticleRetrievalStrategy {

    boolean supports(RetrievalContext context);

    List<RetrievedArticle> retrieve(RetrievalContext context, int limit);

    String strategyName();
}
