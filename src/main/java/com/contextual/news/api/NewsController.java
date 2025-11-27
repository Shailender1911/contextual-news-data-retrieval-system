package com.contextual.news.api;

import com.contextual.news.api.dto.NewsQueryRequest;
import com.contextual.news.api.dto.NewsQueryResponse;
import com.contextual.news.api.dto.TrendingEventRequest;
import com.contextual.news.api.dto.TrendingResponse;
import com.contextual.news.llm.model.ParsedQuery;
import com.contextual.news.llm.model.QueryIntent;
import com.contextual.news.service.NewsQueryService;
import com.contextual.news.service.trending.TrendingService;
import jakarta.validation.Valid;
import java.util.EnumSet;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final NewsQueryService newsQueryService;
    private final TrendingService trendingService;

    public NewsController(NewsQueryService newsQueryService, TrendingService trendingService) {
        this.newsQueryService = newsQueryService;
        this.trendingService = trendingService;
    }

    @PostMapping("/query")
    public ResponseEntity<NewsQueryResponse> query(@Valid @RequestBody NewsQueryRequest request) {
        return ResponseEntity.ok(newsQueryService.query(request));
    }

    @GetMapping("/category")
    public ResponseEntity<NewsQueryResponse> byCategory(@RequestParam String category,
                                                        @RequestParam(required = false) Integer limit) {
        NewsQueryRequest request = new NewsQueryRequest(
            "category:" + category,
            null,
            limit,
            null,
            null
        );
        ParsedQuery parsedQuery = ParsedQuery.create(
            List.of(),
            List.of(category),
            EnumSet.of(QueryIntent.CATEGORY),
            new ParsedQuery.Filters(category, null, null, null, null, null, null, null),
            null,
            true
        );
        return ResponseEntity.ok(newsQueryService.queryWithParsedQuery(request, parsedQuery));
    }

    @GetMapping("/source")
    public ResponseEntity<NewsQueryResponse> bySource(@RequestParam String source,
                                                      @RequestParam(required = false) Integer limit) {
        NewsQueryRequest request = new NewsQueryRequest(
            "source:" + source,
            null,
            limit,
            null,
            null
        );
        ParsedQuery parsedQuery = ParsedQuery.create(
            List.of(),
            List.of(source),
            EnumSet.of(QueryIntent.SOURCE),
            new ParsedQuery.Filters(null, source, null, null, null, null, null, null),
            null,
            true
        );
        return ResponseEntity.ok(newsQueryService.queryWithParsedQuery(request, parsedQuery));
    }

    @GetMapping("/score")
    public ResponseEntity<NewsQueryResponse> byScore(@RequestParam double threshold,
                                                     @RequestParam(required = false) Integer limit) {
        double clamped = Math.max(0.0, Math.min(threshold, 1.0));
        NewsQueryRequest request = new NewsQueryRequest(
            "score >= " + clamped,
            null,
            limit,
            null,
            clamped
        );
        ParsedQuery parsedQuery = ParsedQuery.create(
            List.of(),
            List.of("score"),
            EnumSet.of(QueryIntent.SCORE),
            new ParsedQuery.Filters(null, null, clamped, null, null, null, null, null),
            null,
            true
        );
        return ResponseEntity.ok(newsQueryService.queryWithParsedQuery(request, parsedQuery));
    }

    @GetMapping("/search")
    public ResponseEntity<NewsQueryResponse> search(@RequestParam String query,
                                                    @RequestParam(required = false) Integer limit) {
        NewsQueryRequest request = new NewsQueryRequest(
            query,
            null,
            limit,
            null,
            null
        );
        ParsedQuery parsedQuery = ParsedQuery.create(
            List.of(),
            List.of(),
            EnumSet.of(QueryIntent.SEARCH),
            ParsedQuery.Filters.empty(),
            query,
            true
        );
        return ResponseEntity.ok(newsQueryService.queryWithParsedQuery(request, parsedQuery));
    }

    @GetMapping("/nearby")
    public ResponseEntity<NewsQueryResponse> nearby(@RequestParam double lat,
                                                    @RequestParam double lon,
                                                    @RequestParam(required = false) Double radiusKm,
                                                    @RequestParam(required = false) Integer limit) {
        NewsQueryRequest.Location location = new NewsQueryRequest.Location(lat, lon);
        NewsQueryRequest request = new NewsQueryRequest(
            "nearby:" + lat + "," + lon,
            location,
            limit,
            radiusKm,
            null
        );
        double resolvedRadius = radiusKm != null ? radiusKm : request.resolvedRadiusKm();
        ParsedQuery parsedQuery = ParsedQuery.create(
            List.of(),
            List.of("nearby"),
            EnumSet.of(QueryIntent.NEARBY),
            new ParsedQuery.Filters(null, null, null, resolvedRadius, lat, lon, null, null),
            null,
            true
        );
        return ResponseEntity.ok(newsQueryService.queryWithParsedQuery(request, parsedQuery));
    }

    @GetMapping("/trending")
    public ResponseEntity<TrendingResponse> trending(@RequestParam double lat,
                                                     @RequestParam double lon,
                                                     @RequestParam(required = false) Double radiusKm,
                                                     @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(trendingService.getTrendingFeed(lat, lon, radiusKm, limit));
    }

    @PostMapping("/trending/events")
    public ResponseEntity<Void> ingestTrendingEvent(@Valid @RequestBody TrendingEventRequest request) {
        trendingService.recordEvent(request);
        return ResponseEntity.accepted().build();
    }
}
