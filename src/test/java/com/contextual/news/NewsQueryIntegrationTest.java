package com.contextual.news;

import com.contextual.news.api.dto.NewsQueryRequest;
import com.contextual.news.api.dto.NewsQueryResponse;
import com.contextual.news.service.NewsQueryService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class NewsQueryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4-alpine")
        .withDatabaseName("contextual_news")
        .withUsername("contextual_news")
        .withPassword("contextual_news");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.llm.enabled", () -> false);
    }

    @Autowired
    private NewsQueryService newsQueryService;

    @Test
    void shouldReturnResultsForTechnologyQuery() {
        NewsQueryRequest request = new NewsQueryRequest(
            "Top technology news from the New York Times",
            null,
            3,
            null,
            null
        );
        NewsQueryResponse response = newsQueryService.query(request);
        Assertions.assertThat(response.articles()).isNotEmpty();
    }
}
