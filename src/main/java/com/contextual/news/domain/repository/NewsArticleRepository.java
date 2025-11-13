package com.contextual.news.domain.repository;

import com.contextual.news.domain.model.NewsArticle;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, UUID>,
    JpaSpecificationExecutor<NewsArticle> {
}
