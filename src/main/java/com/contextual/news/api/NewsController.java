package com.contextual.news.api;

import com.contextual.news.api.dto.NewsQueryRequest;
import com.contextual.news.api.dto.NewsQueryResponse;
import com.contextual.news.service.NewsQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final NewsQueryService newsQueryService;

    public NewsController(NewsQueryService newsQueryService) {
        this.newsQueryService = newsQueryService;
    }

    @PostMapping("/query")
    public ResponseEntity<NewsQueryResponse> query(@Valid @RequestBody NewsQueryRequest request) {
        return ResponseEntity.ok(newsQueryService.query(request));
    }
}
