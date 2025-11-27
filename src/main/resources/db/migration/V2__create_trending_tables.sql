DROP TABLE IF EXISTS article_trend_aggregate;

CREATE TABLE article_trend_aggregate (
    bucket_id VARCHAR(128) NOT NULL,
    article_id UUID NOT NULL REFERENCES news_article(id) ON DELETE CASCADE,
    score DOUBLE PRECISION NOT NULL,
    event_count BIGINT NOT NULL,
    last_interaction_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (bucket_id, article_id)
);

CREATE INDEX idx_article_trend_bucket_score ON article_trend_aggregate (bucket_id, score DESC NULLS LAST);
CREATE INDEX idx_article_trend_last_interaction ON article_trend_aggregate (last_interaction_at DESC NULLS LAST);

