package com.contextual.news.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "article_trend_aggregate")
public class ArticleTrendAggregate {

    @EmbeddedId
    private ArticleTrendAggregateId id;

    @Column(nullable = false)
    private double score;

    @Column(name = "event_count", nullable = false)
    private long eventCount;

    @Column(name = "last_interaction_at", nullable = false)
    private OffsetDateTime lastInteractionAt;

    protected ArticleTrendAggregate() {
        // for JPA
    }

    public ArticleTrendAggregate(String bucketId, java.util.UUID articleId, OffsetDateTime occurredAt) {
        this.id = new ArticleTrendAggregateId(bucketId, articleId);
        this.score = 0.0;
        this.eventCount = 0;
        this.lastInteractionAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public ArticleTrendAggregateId getId() {
        return id;
    }

    public double getScore() {
        return score;
    }

    public long getEventCount() {
        return eventCount;
    }

    public OffsetDateTime getLastInteractionAt() {
        return lastInteractionAt;
    }

    public void registerEvent(double increment, OffsetDateTime occurredAt, double lambda) {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        double decayedScore = decayedScore(occurredAt, lambda);
        this.score = decayedScore + increment;
        this.eventCount += 1;
        this.lastInteractionAt = occurredAt;
    }

    public double decayedScore(OffsetDateTime reference, double lambda) {
        if (score <= 0) {
            return 0;
        }
        Objects.requireNonNull(reference, "reference must not be null");
        double minutes = Math.max(0, Duration.between(lastInteractionAt, reference).toMinutes());
        double decay = Math.exp(-lambda * minutes);
        return score * decay;
    }
}

