package com.contextual.news.service.ingest;

import com.contextual.news.config.AppProperties;
import com.contextual.news.domain.model.NewsArticle;
import com.contextual.news.domain.repository.NewsArticleRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NewsDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NewsDataLoader.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AppProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final NewsArticleRepository repository;

    public NewsDataLoader(AppProperties properties,
                          ResourceLoader resourceLoader,
                          ObjectMapper objectMapper,
                          NewsArticleRepository repository) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean bootstrapEnabled = properties.data().isBootstrapEnabled();
        log.debug("NewsDataLoader bootstrapEnabled={}", bootstrapEnabled);
        if (!bootstrapEnabled) {
            log.info("Bootstrap disabled via configuration; skipping news data ingestion");
            return;
        }
        if (repository.count() > 0) {
            log.info("News articles already present, skipping bootstrap");
            return;
        }
        try {
            List<NewsArticleDocument> documents = readDocuments();
            persist(documents);
            log.info("Ingested {} news articles", documents.size());
        } catch (IOException e) {
            log.error("Failed to read news data", e);
        }
    }

    private List<NewsArticleDocument> readDocuments() throws IOException {
        String filePath = properties.data().getFilePath();
        if (filePath == null || filePath.isBlank()) {
            filePath = "classpath:data/news_data.json";
            log.warn("No app.data.file-path configured; defaulting to {}", filePath);
        }
        Resource resource = resourceLoader.getResource(filePath);
        if (!resource.exists()) {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                throw new IOException("Data file not found: " + filePath);
            }
            try (InputStream inputStream = Files.newInputStream(path)) {
                return objectMapper.readValue(inputStream, new TypeReference<List<NewsArticleDocument>>() {
                });
            }
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<List<NewsArticleDocument>>() {
            });
        }
    }

    @Transactional
    protected void persist(List<NewsArticleDocument> documents) {
        List<NewsArticle> batch = new ArrayList<>(documents.size());
        for (NewsArticleDocument document : documents) {
            Set<String> categories = document.category() != null ? new HashSet<>(document.category()) : Set.of();
            NewsArticle entity = new NewsArticle(
                UUID.fromString(document.id()),
                document.title(),
                document.description(),
                document.url(),
                document.offsetDateTime(),
                document.sourceName(),
                document.relevanceScore(),
                document.latitude(),
                document.longitude(),
                categories
            );
            batch.add(entity);
        }
        repository.saveAll(batch);
    }

    public record NewsArticleDocument(
        @JsonProperty("id")
        String id,
        @JsonProperty("title")
        String title,
        @JsonProperty("description")
        String description,
        @JsonProperty("url")
        String url,
        @JsonProperty("publication_date")
        String publicationDate,
        @JsonProperty("source_name")
        String sourceName,
        @JsonProperty("category")
        List<String> category,
        @JsonProperty("relevance_score")
        Double relevanceScore,
        @JsonProperty("latitude")
        Double latitude,
        @JsonProperty("longitude")
        Double longitude
    ) {
        public NewsArticleDocument {
            if (category == null) {
                category = List.of();
            }
        }

        public OffsetDateTime offsetDateTime() {
            if (publicationDate == null || publicationDate.isBlank()) {
                return null;
            }
            try {
                if (publicationDate.endsWith("Z") || publicationDate.contains("+")) {
                    return OffsetDateTime.parse(publicationDate, DATE_FORMATTER);
                }
                return OffsetDateTime.parse(publicationDate + "Z", DATE_FORMATTER);
            } catch (Exception ex) {
                return OffsetDateTime.parse(publicationDate + "Z", DATE_FORMATTER);
            }
        }
    }
}
