package com.contextual.news.llm.client;

import com.contextual.news.llm.model.ArticleEnrichment;
import com.contextual.news.llm.model.ParsedQuery;
import com.contextual.news.service.dto.EnrichmentRequest;
import com.contextual.news.service.dto.QueryUnderstandingContext;

public interface LLMClient {

    ParsedQuery parseQuery(QueryUnderstandingContext context);

    ArticleEnrichment generateEnrichment(EnrichmentRequest request);
}
