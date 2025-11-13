package com.contextual.news;

import com.contextual.news.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties(AppProperties.class)
public class ContextualNewsDataRetrievalSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(ContextualNewsDataRetrievalSystemApplication.class, args);
	}

}
