package com.contextual.news.service.retrieval;

import com.contextual.news.api.dto.NewsQueryRequest;
import com.contextual.news.llm.model.ParsedQuery;

public record RetrievalContext(
    NewsQueryRequest request,
    ParsedQuery parsedQuery
) {
}
