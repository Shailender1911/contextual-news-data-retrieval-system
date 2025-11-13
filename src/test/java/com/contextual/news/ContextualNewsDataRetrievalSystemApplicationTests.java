package com.contextual.news;

import com.contextual.news.service.ingest.NewsDataLoader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@TestPropertySource(properties = {
    "app.data.bootstrap-enabled=false",
    "spring.flyway.enabled=false"
})
class ContextualNewsDataRetrievalSystemApplicationTests {

    @MockBean
    private NewsDataLoader newsDataLoader;

	@Test
	void contextLoads() {
	}
}
