package com.contextual.news.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ArticleTrendAggregateId implements Serializable {

    @Column(name = "bucket_id", nullable = false, length = 128)
    private String bucketId;

    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    protected ArticleTrendAggregateId() {
        // for JPA
    }

    public ArticleTrendAggregateId(String bucketId, UUID articleId) {
        this.bucketId = Objects.requireNonNull(bucketId, "bucketId must not be null");
        this.articleId = Objects.requireNonNull(articleId, "articleId must not be null");
    }

    public String getBucketId() {
        return bucketId;
    }

    public UUID getArticleId() {
        return articleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ArticleTrendAggregateId that = (ArticleTrendAggregateId) o;
        return bucketId.equals(that.bucketId) && articleId.equals(that.articleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucketId, articleId);
    }
}

