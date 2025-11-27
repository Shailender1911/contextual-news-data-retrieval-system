package com.contextual.news.domain.repository;

import com.contextual.news.domain.model.ArticleTrendAggregate;
import com.contextual.news.domain.model.ArticleTrendAggregateId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleTrendAggregateRepository extends JpaRepository<ArticleTrendAggregate, ArticleTrendAggregateId> {

    List<ArticleTrendAggregate> findByIdBucketIdIn(Collection<String> bucketIds);
}

